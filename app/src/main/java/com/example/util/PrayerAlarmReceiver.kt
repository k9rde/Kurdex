package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.PreferencesManager
import com.example.model.PrayerType
import com.example.widget.PrayerWidgetProvider

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Reschedule after reboot or app update
            val prefs = PreferencesManager(context)
            val city = prefs.getSelectedCity()
            val cal = java.util.Calendar.getInstance(PrayerCalculator.getTimeZoneForOffset(city.timezone))
            val times = PrayerCalculator.calculateTimes(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                city.latitude,
                city.longitude,
                city.timezone
            )
            val cityName = if (city.nameAr.isNotEmpty()) city.nameAr else city.name
            AdhanNotificationHelper.scheduleAllPrayers(context, times, cityName)
            return
        }

        when (action) {
            AdhanNotificationHelper.ACTION_PRAYER_ALARM -> {
                val prayerTypeStr = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE) ?: ""
                val prayerName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME) ?: "الصلاة"
                val cityName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_CITY_NAME) ?: ""

                val notificationId = try {
                    PrayerType.valueOf(prayerTypeStr).ordinal + 100
                } catch (_: Exception) {
                    100
                }

                AdhanNotificationHelper.cancelOngoingCountdownNotification(context)
                AdhanNotificationHelper.showAdhanNotification(
                    context = context,
                    prayerNameAr = prayerName,
                    cityName = cityName,
                    notificationId = notificationId
                )
                PrayerWidgetProvider.updateAllWidgets(context)
            }

            AdhanNotificationHelper.ACTION_PRE_PRAYER_ALARM -> {
                val prayerTypeStr = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE) ?: ""
                val prayerName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME) ?: "الصلاة"
                val cityName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_CITY_NAME) ?: ""

                val notificationId = try {
                    PrayerType.valueOf(prayerTypeStr).ordinal + 300
                } catch (_: Exception) {
                    300
                }

                AdhanNotificationHelper.showPrePrayerReminderNotification(
                    context = context,
                    prayerNameAr = prayerName,
                    cityName = cityName,
                    notificationId = notificationId
                )
            }

            AdhanNotificationHelper.ACTION_POST_PRAYER_FOLLOWUP_ALARM -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isPostPrayerFollowupEnabled) return

                val prayerTypeStr = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE) ?: ""
                val prayerName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME) ?: "الصلاة"
                val cityName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_CITY_NAME) ?: ""
                val isSnoozeRetry = intent.getBooleanExtra(AdhanNotificationHelper.EXTRA_IS_SNOOZE_RETRY, false)

                // If user already marked this prayer completed in the tracker today, do not disturb
                if (prayerTypeStr.isNotBlank() &&
                    prefs.getCompletedPrayersForDate(prefs.getTodayDateKey()).contains(prayerTypeStr)
                ) {
                    return
                }

                val notificationId = try {
                    PrayerType.valueOf(prayerTypeStr).ordinal + 400
                } catch (_: Exception) {
                    400
                }

                AdhanNotificationHelper.showPostPrayerFollowupNotification(
                    context = context,
                    prayerTypeStr = prayerTypeStr,
                    prayerNameAr = prayerName,
                    cityName = cityName,
                    notificationId = notificationId,
                    isSnoozeRetry = isSnoozeRetry
                )
            }

            AdhanNotificationHelper.ACTION_PRAYED_YES -> {
                val prayerTypeStr = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE) ?: ""
                val prayerName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME) ?: "الصلاة"
                val notificationId = intent.getIntExtra(AdhanNotificationHelper.EXTRA_NOTIFICATION_ID, 400)

                if (prayerTypeStr.isNotBlank()) {
                    val prefs = PreferencesManager(context)
                    prefs.markPrayerCompleted(prefs.getTodayDateKey(), prayerTypeStr)
                }

                AdhanNotificationHelper.showBriefFollowupConfirmation(
                    context = context,
                    notificationId = notificationId,
                    title = "تقبّل الله طاعتك 🤍",
                    message = "تم تسجيل صلاة $prayerName في سجل صلواتك اليوم ✓"
                )
                PrayerWidgetProvider.updateAllWidgets(context)
            }

            AdhanNotificationHelper.ACTION_PRAYED_SOON -> {
                val prayerTypeStr = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE) ?: ""
                val prayerName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME) ?: "الصلاة"
                val cityName = intent.getStringExtra(AdhanNotificationHelper.EXTRA_CITY_NAME) ?: ""
                val notificationId = intent.getIntExtra(AdhanNotificationHelper.EXTRA_NOTIFICATION_ID, 400)
                val isSnoozeRetry = intent.getBooleanExtra(AdhanNotificationHelper.EXTRA_IS_SNOOZE_RETRY, false)

                if (!isSnoozeRetry && prayerTypeStr.isNotBlank()) {
                    AdhanNotificationHelper.schedulePostPrayerFollowupSnooze(
                        context = context,
                        prayerTypeStr = prayerTypeStr,
                        prayerNameAr = prayerName,
                        cityName = cityName
                    )
                }

                AdhanNotificationHelper.showBriefFollowupConfirmation(
                    context = context,
                    notificationId = notificationId,
                    title = "أعانك الله وتقبّل منك 🕌",
                    message = if (!isSnoozeRetry) {
                        "هيا إلى صلاة $prayerName، وسنذكّرك بلطف بعد 15 دقيقة إذا احتجت ذلك."
                    } else {
                        "بارك الله فيك، بادر إلى صلاة $prayerName الآن تقبّل الله منك 🤍"
                    }
                )
            }

            AdhanNotificationHelper.ACTION_DISMISS_ONGOING -> {
                AdhanNotificationHelper.cancelOngoingCountdownNotification(context)
            }
        }
    }
}
