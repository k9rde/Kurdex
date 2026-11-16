package com.example.worker

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.OnlinePrayerSyncManager
import com.example.data.PreferencesManager
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.DayPrayerTimes
import com.example.model.JuristicMethod
import com.example.service.PrayerTimeNotificationService
import com.example.util.AdhanNotificationHelper
import com.example.util.PrayerCalculator
import com.example.widget.PrayerWidgetProvider
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WorkManager CoroutineWorker that refreshes daily prayer times in the background when network
 * connectivity is available and reacts to significant location changes or date rollovers.
 */
class PrayerTimesSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = PreferencesManager(context)
        val triggerReason = inputData.getString(KEY_TRIGGER_REASON) ?: REASON_PERIODIC
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)

        if (!prefs.isAutoSyncOnlineEnabled && !forceRefresh && triggerReason != REASON_LOCATION_CHANGED) {
            return Result.success(
                workDataOf(
                    KEY_OUT_STATUS to "skipped_disabled",
                    KEY_OUT_LOCATION to prefs.getSelectedCity().nameAr
                )
            )
        }

        return try {
            val nowMillis = System.currentTimeMillis()
            val resolvedLocation = resolveTargetLocation(context, prefs, nowMillis)
            val cityTz = PrayerCalculator.getTimeZoneForOffset(resolvedLocation.timezone)
            val cal = Calendar.getInstance(cityTz).apply { timeInMillis = nowMillis }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dateKey = "${year}_${month}_${day}"

            val calcMethod = CalculationMethod.entries.getOrElse(prefs.calculationMethodIndex) {
                CalculationMethod.MWL
            }
            val juristicMethod = JuristicMethod.entries.getOrElse(prefs.juristicMethodIndex) {
                JuristicMethod.STANDARD
            }

            val cacheKey =
                "${resolvedLocation.id}_${year}_${month}_${day}_${calcMethod.name}_${juristicMethod.name}"

            val shouldFetchOnline = shouldPerformSync(
                prefs = prefs,
                currentLat = resolvedLocation.latitude,
                currentLng = resolvedLocation.longitude,
                todayDateKey = dateKey,
                cacheKey = cacheKey,
                triggerReason = triggerReason,
                forceRefresh = forceRefresh
            )

            var usedOnlineData = false
            var finalTimes: DayPrayerTimes? = null

            if (shouldFetchOnline && OnlinePrayerSyncManager.isNetworkAvailable(context)) {
                val onlineTimes = OnlinePrayerSyncManager.fetchOnlineDayPrayerTimes(
                    year = year,
                    month = month,
                    day = day,
                    latitude = resolvedLocation.latitude,
                    longitude = resolvedLocation.longitude,
                    timeZone = resolvedLocation.timezone,
                    methodId = calcMethod.aladhanId,
                    juristicFactor = juristicMethod.shadowFactor
                )

                if (onlineTimes != null) {
                    val cacheJson = JSONObject().apply {
                        put("fajr", onlineTimes.fajr)
                        put("sunrise", onlineTimes.sunrise)
                        put("dhuhr", onlineTimes.dhuhr)
                        put("asr", onlineTimes.asr)
                        put("maghrib", onlineTimes.maghrib)
                        put("isha", onlineTimes.isha)
                        put("midnight", onlineTimes.midnight)
                        put("hijri", onlineTimes.hijriFormatted)
                    }
                    prefs.saveCachedDayTimings(cacheKey, cacheJson.toString())
                    prefs.lastSyncTimestamp = nowMillis
                    prefs.recordSyncedLocationAndDate(
                        latitude = resolvedLocation.latitude,
                        longitude = resolvedLocation.longitude,
                        dateKey = dateKey
                    )
                    finalTimes = onlineTimes
                    usedOnlineData = true
                } else if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    // Schedule offline astronomical fallback before retrying online fetch later
                    val fallbackTimes = calculateAstronomicalTimes(
                        year, month, day, resolvedLocation, calcMethod, juristicMethod, prefs
                    )
                    applyUpdatedPrayerTimes(context, resolvedLocation, fallbackTimes)
                    return Result.retry()
                }
            }

            if (finalTimes == null) {
                finalTimes = calculateAstronomicalTimes(
                    year, month, day, resolvedLocation, calcMethod, juristicMethod, prefs
                )
                if (!shouldFetchOnline && prefs.lastSyncedDateKey.isBlank()) {
                    prefs.recordSyncedLocationAndDate(
                        latitude = resolvedLocation.latitude,
                        longitude = resolvedLocation.longitude,
                        dateKey = dateKey
                    )
                }
            }

            applyUpdatedPrayerTimes(context, resolvedLocation, finalTimes)

            val output: Data = workDataOf(
                KEY_OUT_STATUS to if (usedOnlineData) "synced_online" else "updated_astronomical",
                KEY_OUT_LOCATION to (resolvedLocation.nameAr.ifEmpty { resolvedLocation.name }),
                KEY_OUT_LATITUDE to resolvedLocation.latitude,
                KEY_OUT_LONGITUDE to resolvedLocation.longitude,
                KEY_OUT_ONLINE_USED to usedOnlineData
            )
            Result.success(output)
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun applyUpdatedPrayerTimes(
        context: Context,
        city: City,
        times: DayPrayerTimes
    ) {
        val cityLabel = city.nameAr.ifEmpty { city.name }
        AdhanNotificationHelper.createNotificationChannel(context)
        AdhanNotificationHelper.scheduleAllPrayers(context, times, cityLabel)
        PrayerWidgetProvider.updateAllWidgets(context)
        PrayerTimeNotificationService.syncServiceState(context)
    }

    private fun calculateAstronomicalTimes(
        year: Int,
        month: Int,
        day: Int,
        city: City,
        calcMethod: CalculationMethod,
        juristicMethod: JuristicMethod,
        prefs: PreferencesManager
    ): DayPrayerTimes {
        return PrayerCalculator.calculateTimes(
            year = year,
            month = month,
            day = day,
            lat = city.latitude,
            lng = city.longitude,
            timeZone = city.timezone,
            method = calcMethod,
            juristic = juristicMethod,
            fajrOffset = prefs.getPrayerOffset("fajr"),
            sunriseOffset = prefs.getPrayerOffset("sunrise"),
            dhuhrOffset = prefs.getPrayerOffset("dhuhr"),
            asrOffset = prefs.getPrayerOffset("asr"),
            maghribOffset = prefs.getPrayerOffset("maghrib"),
            ishaOffset = prefs.getPrayerOffset("isha")
        )
    }

    companion object {
        const val KEY_TRIGGER_REASON = "trigger_reason"
        const val KEY_FORCE_REFRESH = "force_refresh"

        const val REASON_PERIODIC = "periodic"
        const val REASON_LOCATION_CHANGED = "location_changed"
        const val REASON_MANUAL = "manual"

        const val KEY_OUT_STATUS = "out_status"
        const val KEY_OUT_LOCATION = "out_location"
        const val KEY_OUT_LATITUDE = "out_latitude"
        const val KEY_OUT_LONGITUDE = "out_longitude"
        const val KEY_OUT_ONLINE_USED = "out_online_used"

        const val SIGNIFICANT_LOCATION_CHANGE_METERS = 3_000.0
        private const val MAX_RETRY_ATTEMPTS = 3

        fun resolveTargetLocation(
            context: Context,
            prefs: PreferencesManager,
            nowMillis: Long = System.currentTimeMillis()
        ): City {
            val savedCity = prefs.getSelectedCity()
            val liveLocation = PrayerTimeNotificationService.getBestLastKnownLocation(context)
                ?: return savedCity

            val movedMeters = calculateDistanceMeters(
                savedCity.latitude,
                savedCity.longitude,
                liveLocation.latitude,
                liveLocation.longitude
            )

            return if (movedMeters >= SIGNIFICANT_LOCATION_CHANGE_METERS) {
                val tzHours = TimeZone.getDefault().getOffset(nowMillis) / 3_600_000.0
                val updatedCity = City(
                    id = "custom_gps",
                    name = "موقعي الحالي (GPS)",
                    nameAr = "موقعي الحالي (GPS)",
                    country = "تحديد تلقائي",
                    countryAr = "تحديد تلقائي",
                    latitude = liveLocation.latitude,
                    longitude = liveLocation.longitude,
                    timezone = tzHours
                )
                prefs.setSelectedCity(updatedCity)
                updatedCity
            } else {
                savedCity
            }
        }

        fun shouldPerformSync(
            prefs: PreferencesManager,
            currentLat: Double,
            currentLng: Double,
            todayDateKey: String,
            cacheKey: String,
            triggerReason: String,
            forceRefresh: Boolean
        ): Boolean {
            if (forceRefresh || triggerReason == REASON_LOCATION_CHANGED) return true
            if (prefs.lastSyncedDateKey != todayDateKey) return true
            if (prefs.getCachedDayTimings(cacheKey).isNullOrEmpty()) return true

            val prevLat = prefs.lastSyncedLatitude ?: return true
            val prevLng = prefs.lastSyncedLongitude ?: return true
            return calculateDistanceMeters(prevLat, prevLng, currentLat, currentLng) >=
                SIGNIFICANT_LOCATION_CHANGE_METERS
        }

        fun calculateDistanceMeters(
            lat1: Double,
            lng1: Double,
            lat2: Double,
            lng2: Double
        ): Double {
            val results = FloatArray(1)
            return try {
                Location.distanceBetween(lat1, lng1, lat2, lng2, results)
                results[0].toDouble()
            } catch (_: Exception) {
                val earthRadiusMeters = 6_371_008.8
                val dLat = Math.toRadians(lat2 - lat1)
                val dLng = Math.toRadians(lng2 - lng1)
                val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLng / 2).pow(2)
                2.0 * earthRadiusMeters * atan2(sqrt(a), sqrt(1.0 - a))
            }
        }
    }
}
