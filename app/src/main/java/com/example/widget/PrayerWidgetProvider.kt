package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.model.CalculationMethod
import com.example.model.DayPrayerTimes
import com.example.model.JuristicMethod
import com.example.model.PrayerType
import com.example.util.PrayerCalculator
import java.util.Calendar

class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_WIDGET_REFRESH ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelBoundaryRefresh(context)
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"
        private const val WIDGET_REFRESH_REQUEST_CODE = 1901

        private data class PrayerTileBinding(
            val type: PrayerType,
            val tileId: Int,
            val labelId: Int,
            val timeId: Int,
            val periodId: Int,
            val formattedTime: (DayPrayerTimes) -> String
        )

        private val tileBindings = listOf(
            PrayerTileBinding(
                type = PrayerType.FAJR,
                tileId = R.id.widget_tile_fajr,
                labelId = R.id.widget_fajr_label,
                timeId = R.id.widget_fajr_time,
                periodId = R.id.widget_fajr_period,
                formattedTime = { it.fajr }
            ),
            PrayerTileBinding(
                type = PrayerType.SUNRISE,
                tileId = R.id.widget_tile_sunrise,
                labelId = R.id.widget_sunrise_label,
                timeId = R.id.widget_sunrise_time,
                periodId = R.id.widget_sunrise_period,
                formattedTime = { it.sunrise }
            ),
            PrayerTileBinding(
                type = PrayerType.DHUHR,
                tileId = R.id.widget_tile_dhuhr,
                labelId = R.id.widget_dhuhr_label,
                timeId = R.id.widget_dhuhr_time,
                periodId = R.id.widget_dhuhr_period,
                formattedTime = { it.dhuhr }
            ),
            PrayerTileBinding(
                type = PrayerType.ASR,
                tileId = R.id.widget_tile_asr,
                labelId = R.id.widget_asr_label,
                timeId = R.id.widget_asr_time,
                periodId = R.id.widget_asr_period,
                formattedTime = { it.asr }
            ),
            PrayerTileBinding(
                type = PrayerType.MAGHRIB,
                tileId = R.id.widget_tile_maghrib,
                labelId = R.id.widget_maghrib_label,
                timeId = R.id.widget_maghrib_time,
                periodId = R.id.widget_maghrib_period,
                formattedTime = { it.maghrib }
            ),
            PrayerTileBinding(
                type = PrayerType.ISHA,
                tileId = R.id.widget_tile_isha,
                labelId = R.id.widget_isha_label,
                timeId = R.id.widget_isha_time,
                periodId = R.id.widget_isha_period,
                formattedTime = { it.isha }
            )
        )

        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
                if (ids.isEmpty()) return
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            } catch (_: Exception) {
            }
        }

        internal fun buildWidgetRemoteViews(
            context: Context,
            nowMillis: Long = System.currentTimeMillis(),
            elapsedRealtimeMillis: Long = SystemClock.elapsedRealtime()
        ): Pair<RemoteViews, Long?> {
            val prefs = PreferencesManager(context)
            val city = prefs.getSelectedCity()
            val calcMethod = CalculationMethod.values().getOrElse(prefs.calculationMethodIndex) { CalculationMethod.MWL }
            val juristic = JuristicMethod.values().getOrElse(prefs.juristicMethodIndex) { JuristicMethod.STANDARD }

            val cityTz = PrayerCalculator.getTimeZoneForOffset(city.timezone)
            val cal = Calendar.getInstance(cityTz).apply { timeInMillis = nowMillis }
            val times = PrayerCalculator.calculateTimes(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
                lat = city.latitude,
                lng = city.longitude,
                timeZone = city.timezone,
                method = calcMethod,
                juristic = juristic,
                fajrOffset = prefs.getPrayerOffset("fajr"),
                sunriseOffset = prefs.getPrayerOffset("sunrise"),
                dhuhrOffset = prefs.getPrayerOffset("dhuhr"),
                asrOffset = prefs.getPrayerOffset("asr"),
                maghribOffset = prefs.getPrayerOffset("maghrib"),
                ishaOffset = prefs.getPrayerOffset("isha")
            )

            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

            val cityName = city.nameAr.ifEmpty { city.name }
            views.setTextViewText(R.id.widget_city_text, cityName)
            views.setTextViewText(R.id.widget_hijri_date, times.hijriFormatted)

            val completedToday = prefs.getCompletedPrayersForDate(prefs.getTodayDateKey())
            val obligatoryKeys = setOf(
                PrayerType.FAJR.name,
                PrayerType.DHUHR.name,
                PrayerType.ASR.name,
                PrayerType.MAGHRIB.name,
                PrayerType.ISHA.name
            )
            val completedCount = completedToday.count { it in obligatoryKeys }
            views.setTextViewText(R.id.widget_completed_badge, "تمت $completedCount/5")

            val progressPercent = (times.progress * 100f).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_prayer_progress, 100, progressPercent, false)

            val next = times.nextPrayer
            val nextBoundaryMillis: Long?
            if (next != null) {
                views.setTextViewText(
                    R.id.widget_next_prayer_label,
                    "المتبقي ${toPrefixedArabicPrayerName(next.type)}"
                )
                views.setTextViewText(R.id.widget_next_prayer_time, next.timeFormatted)

                val diffMillis = (next.timestampMillis - nowMillis).coerceAtLeast(1000L)
                val baseRealtime = elapsedRealtimeMillis + diffMillis
                views.setChronometerCountDown(R.id.widget_countdown_chronometer, true)
                views.setChronometer(R.id.widget_countdown_chronometer, baseRealtime, null, true)
                nextBoundaryMillis = next.timestampMillis + 1000L
            } else {
                views.setTextViewText(R.id.widget_next_prayer_label, "المتبقي للفجر")
                views.setTextViewText(R.id.widget_next_prayer_time, times.fajr)
                views.setChronometerCountDown(R.id.widget_countdown_chronometer, true)
                views.setChronometer(R.id.widget_countdown_chronometer, elapsedRealtimeMillis, null, false)
                nextBoundaryMillis = null
            }

            for (binding in tileBindings) {
                val (clockPart, periodPart) = splitTimeAndPeriod(binding.formattedTime(times))
                views.setTextViewText(binding.timeId, clockPart)
                views.setTextViewText(binding.periodId, periodPart)

                val isNextTile = next?.type == binding.type
                val isCompletedTile = binding.type != PrayerType.SUNRISE && completedToday.contains(binding.type.name)

                when {
                    isNextTile -> {
                        views.setInt(binding.tileId, "setBackgroundResource", R.drawable.widget_tile_active_bg)
                        views.setTextViewText(binding.labelId, binding.type.titleAr)
                        views.setTextColor(binding.labelId, 0xFFFFE89E.toInt())
                        views.setTextColor(binding.timeId, 0xFFFFFFFF.toInt())
                        views.setTextColor(binding.periodId, 0xFFFFE89E.toInt())
                    }
                    isCompletedTile -> {
                        views.setInt(binding.tileId, "setBackgroundResource", R.drawable.widget_tile_completed_bg)
                        views.setTextViewText(binding.labelId, "${binding.type.titleAr} ✓")
                        views.setTextColor(binding.labelId, 0xFF86EFAC.toInt())
                        views.setTextColor(binding.timeId, 0xFFEBF5EE.toInt())
                        views.setTextColor(binding.periodId, 0xB386EFAC.toInt())
                    }
                    else -> {
                        views.setInt(binding.tileId, "setBackgroundResource", R.drawable.widget_tile_bg)
                        views.setTextViewText(binding.labelId, binding.type.titleAr)
                        views.setTextColor(binding.labelId, 0xCCFFFFFF.toInt())
                        views.setTextColor(binding.timeId, 0xFFFFFFFF.toInt())
                        views.setTextColor(binding.periodId, 0x99F3E5AB.toInt())
                    }
                }
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                999,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            return views to nextBoundaryMillis
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val (views, nextBoundaryMillis) = buildWidgetRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (nextBoundaryMillis != null) {
                scheduleBoundaryRefresh(context, nextBoundaryMillis)
            }
        }

        internal fun toPrefixedArabicPrayerName(type: PrayerType): String {
            return when (type) {
                PrayerType.FAJR -> "للفجر"
                PrayerType.SUNRISE -> "للشروق"
                PrayerType.DHUHR -> "للظهر"
                PrayerType.ASR -> "للعصر"
                PrayerType.MAGHRIB -> "للمغرب"
                PrayerType.ISHA -> "للعشاء"
            }
        }

        internal fun splitTimeAndPeriod(rawFormatted: String): Pair<String, String> {
            val trimmed = rawFormatted.trim()
            val spaceIdx = trimmed.lastIndexOf(' ')
            return if (spaceIdx > 0 && spaceIdx < trimmed.length - 1) {
                trimmed.substring(0, spaceIdx).trim() to trimmed.substring(spaceIdx + 1).trim()
            } else {
                trimmed to ""
            }
        }

        private fun createRefreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            return PendingIntent.getBroadcast(
                context,
                WIDGET_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleBoundaryRefresh(context: Context, triggerAtMillis: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val pi = createRefreshPendingIntent(context)
                val safeTrigger = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 2000L)
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, safeTrigger, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC, safeTrigger, pi)
                }
            } catch (_: Exception) {
            }
        }

        private fun cancelBoundaryRefresh(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                alarmManager.cancel(createRefreshPendingIntent(context))
            } catch (_: Exception) {
            }
        }
    }
}
