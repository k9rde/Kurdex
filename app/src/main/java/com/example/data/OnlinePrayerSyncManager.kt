package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.model.DayPrayerTimes
import com.example.model.PrayerItem
import com.example.model.PrayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object OnlinePrayerSyncManager {
    private const val TAG = "OnlinePrayerSync"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches exact official prayer times for a specific month or day from AlAdhan API.
     * AlAdhan API is the global standard for prayer times by country and calculation method.
     */
    suspend fun fetchOnlineDayPrayerTimes(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timeZone: Double,
        methodId: Int,
        juristicFactor: Double
    ): DayPrayerTimes? = withContext(Dispatchers.IO) {
        val school = if (juristicFactor >= 2.0) 1 else 0 // 0 = Shafi/Standard, 1 = Hanafi
        val cityTz = com.example.util.PrayerCalculator.getTimeZoneForOffset(timeZone)

        // AlAdhan endpoint: https://api.aladhan.com/v1/timings/{timestamp}?latitude=X&longitude=Y&method=M&school=S
        val cal = Calendar.getInstance(cityTz)
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val timestampSeconds = cal.timeInMillis / 1000

        val url = "https://api.aladhan.com/v1/timings/$timestampSeconds?latitude=$latitude&longitude=$longitude&method=$methodId&school=$school"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PrayerTimesApp/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "API request failed with HTTP code: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: return@withContext null
                val timings = data.optJSONObject("timings") ?: return@withContext null
                val dateObj = data.optJSONObject("date")
                val hijriObj = dateObj?.optJSONObject("hijri")

                fun cleanTime(raw: String): String {
                    val parts = raw.split(" ")
                    return parts[0].trim()
                }

                fun parseHourMinute(timeStr: String): Pair<Int, Int> {
                    val clean = timeStr.replace("ص", "").replace("م", "").trim()
                    val splits = clean.split(":")
                    var h = splits.getOrNull(0)?.trim()?.toIntOrNull() ?: 12
                    val m = splits.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    if (timeStr.contains("م") && h < 12) h += 12
                    if (timeStr.contains("ص") && h == 12) h = 0
                    return Pair(h, m)
                }

                fun format12(timeStr: String): String {
                    val (h, m) = parseHourMinute(timeStr)
                    return com.example.util.PrayerCalculator.format12FromHourMinute(h, m)
                }

                val fajrRaw = cleanTime(timings.optString("Fajr", "05:00"))
                val sunriseRaw = cleanTime(timings.optString("Sunrise", "06:20"))
                val dhuhrRaw = cleanTime(timings.optString("Dhuhr", "12:15"))
                val asrRaw = cleanTime(timings.optString("Asr", "15:30"))
                val maghribRaw = cleanTime(timings.optString("Maghrib", "18:00"))
                val ishaRaw = cleanTime(timings.optString("Isha", "19:30"))
                val midnightRaw = cleanTime(timings.optString("Midnight", "23:45"))

                val fajr12 = format12(fajrRaw)
                val sunrise12 = format12(sunriseRaw)
                val dhuhr12 = format12(dhuhrRaw)
                val asr12 = format12(asrRaw)
                val maghrib12 = format12(maghribRaw)
                val isha12 = format12(ishaRaw)
                val midnight12 = format12(midnightRaw)

                // Format Hijri date
                val hijriDay = hijriObj?.optString("day", "$day") ?: "$day"
                val hijriMonthAr = hijriObj?.optJSONObject("month")?.optString("ar")
                    ?: hijriObj?.optJSONObject("month")?.optString("en") ?: ""
                val hijriYear = hijriObj?.optString("year", "1448") ?: "1448"
                val hijriFormatted = "$hijriDay $hijriMonthAr $hijriYear هـ"

                val dateFormatted = String.format(Locale.getDefault(), "%02d.%02d.%04d", day, month, year)

                // Build Prayer Items using City Timezone
                fun toTimestamp(h: Int, m: Int): Long {
                    val c = Calendar.getInstance(cityTz)
                    c.set(year, month - 1, day, h, m, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    return c.timeInMillis
                }

                val rawItems = listOf(
                    PrayerItem(PrayerType.FAJR, fajr12, parseHourMinute(fajrRaw).first, parseHourMinute(fajrRaw).second, toTimestamp(parseHourMinute(fajrRaw).first, parseHourMinute(fajrRaw).second)),
                    PrayerItem(PrayerType.SUNRISE, sunrise12, parseHourMinute(sunriseRaw).first, parseHourMinute(sunriseRaw).second, toTimestamp(parseHourMinute(sunriseRaw).first, parseHourMinute(sunriseRaw).second)),
                    PrayerItem(PrayerType.DHUHR, dhuhr12, parseHourMinute(dhuhrRaw).first, parseHourMinute(dhuhrRaw).second, toTimestamp(parseHourMinute(dhuhrRaw).first, parseHourMinute(dhuhrRaw).second)),
                    PrayerItem(PrayerType.ASR, asr12, parseHourMinute(asrRaw).first, parseHourMinute(asrRaw).second, toTimestamp(parseHourMinute(asrRaw).first, parseHourMinute(asrRaw).second)),
                    PrayerItem(PrayerType.MAGHRIB, maghrib12, parseHourMinute(maghribRaw).first, parseHourMinute(maghribRaw).second, toTimestamp(parseHourMinute(maghribRaw).first, parseHourMinute(maghribRaw).second)),
                    PrayerItem(PrayerType.ISHA, isha12, parseHourMinute(ishaRaw).first, parseHourMinute(ishaRaw).second, toTimestamp(parseHourMinute(ishaRaw).first, parseHourMinute(ishaRaw).second))
                )

                val now = System.currentTimeMillis()
                var nextItem: PrayerItem? = null
                var prevItem: PrayerItem? = null

                for (i in rawItems.indices) {
                    if (now < rawItems[i].timestampMillis) {
                        nextItem = rawItems[i]
                        prevItem = if (i > 0) rawItems[i - 1] else rawItems.last()
                        break
                    }
                }

                if (nextItem == null) {
                    val (fajrH, fajrM) = parseHourMinute(fajrRaw)
                    val tomorrowCal = Calendar.getInstance(cityTz)
                    tomorrowCal.set(year, month - 1, day, fajrH, fajrM, 0)
                    tomorrowCal.set(Calendar.MILLISECOND, 0)
                    tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
                    nextItem = rawItems.first().copy(timestampMillis = tomorrowCal.timeInMillis)
                    prevItem = rawItems.last()
                }

                val markedItems = rawItems.map { item ->
                    val isNext = item.type == nextItem?.type
                    val isCurrent = item.type == prevItem?.type
                    item.copy(isCurrent = isCurrent, isNext = isNext)
                }

                // Countdown
                val diffMillis = (nextItem.timestampMillis - now).coerceAtLeast(0L)
                val remainingSecs = diffMillis / 1000
                val remH = remainingSecs / 3600
                val remM = (remainingSecs % 3600) / 60
                val remS = remainingSecs % 60
                val countdown = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)

                val totalInterval = (nextItem.timestampMillis - (prevItem?.timestampMillis ?: (nextItem.timestampMillis - 1000L))).toFloat()
                val elapsed = (now - (prevItem?.timestampMillis ?: now)).toFloat().coerceAtLeast(0f)
                val progress = if (totalInterval > 0) (elapsed / totalInterval).coerceIn(0f, 1f) else 0f

                // Kerahat
                val sunriseTime = rawItems.find { it.type == PrayerType.SUNRISE }?.timestampMillis ?: 0L
                val dhuhrTime = rawItems.find { it.type == PrayerType.DHUHR }?.timestampMillis ?: 0L
                val maghribTime = rawItems.find { it.type == PrayerType.MAGHRIB }?.timestampMillis ?: 0L
                val kerahat45Min = 45 * 60 * 1000L

                var isKerahat = false
                var kerahatMsg: String? = null
                if (now in sunriseTime..(sunriseTime + kerahat45Min)) {
                    isKerahat = true
                    kerahatMsg = "وقت كراهة (شروق الشمس)"
                } else if (now in (dhuhrTime - kerahat45Min)..dhuhrTime) {
                    isKerahat = true
                    kerahatMsg = "وقت كراهة (استواء الشمس قبل الظهر)"
                } else if (now in (maghribTime - kerahat45Min)..maghribTime) {
                    isKerahat = true
                    kerahatMsg = "وقت كراهة (احمرار وغروب الشمس)"
                }

                return@withContext DayPrayerTimes(
                    dateFormatted = dateFormatted,
                    hijriFormatted = hijriFormatted,
                    fajr = fajr12,
                    sunrise = sunrise12,
                    dhuhr = dhuhr12,
                    asr = asr12,
                    maghrib = maghrib12,
                    isha = isha12,
                    midnight = midnight12,
                    items = markedItems,
                    nextPrayer = nextItem,
                    previousPrayer = prevItem,
                    countdownFormatted = countdown,
                    progress = progress,
                    isKerahat = isKerahat,
                    kerahatMessage = kerahatMsg,
                    isOnlineVerified = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching online prayer times", e)
            return@withContext null
        }
    }
}
