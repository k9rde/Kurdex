package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.model.AdhanVoice
import com.example.model.DayPrayerTimes
import com.example.model.PrayerType
import java.util.Calendar

object AdhanNotificationHelper {

    const val CHANNEL_ID = "adhan_prayer_channel_v2"
    const val CHANNEL_ID_REMINDER = "pre_prayer_reminder_channel"
    const val CHANNEL_ID_FOLLOWUP = "post_prayer_followup_channel"
    const val CHANNEL_ID_ONGOING = "ongoing_countdown_channel"
    const val NOTIFICATION_ID_TEST = 777
    const val NOTIFICATION_ID_ONGOING = 888
    const val ACTION_PRAYER_ALARM = "com.example.ACTION_PRAYER_ALARM"
    const val ACTION_PRE_PRAYER_ALARM = "com.example.ACTION_PRE_PRAYER_ALARM"
    const val ACTION_POST_PRAYER_FOLLOWUP_ALARM = "com.example.ACTION_POST_PRAYER_FOLLOWUP_ALARM"
    const val ACTION_PRAYED_YES = "com.example.ACTION_PRAYED_YES"
    const val ACTION_PRAYED_SOON = "com.example.ACTION_PRAYED_SOON"
    const val ACTION_DISMISS_ONGOING = "com.example.ACTION_DISMISS_ONGOING"
    const val ACTION_STOP_ADHAN = "com.example.ACTION_STOP_ADHAN"
    const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_CITY_NAME = "extra_city_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_SNOOZE_RETRY = "extra_is_snooze_retry"
    const val POST_PRAYER_FOLLOWUP_DELAY_MS = 60 * 60 * 1000L
    const val POST_PRAYER_SNOOZE_DELAY_MS = 15 * 60 * 1000L

    private var activeMediaPlayer: MediaPlayer? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "إشعارات الأذان (الله أكبر الله أكبر)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مواقيت الصلاة بصوت كبار أئمة ومؤذني العالم الإسلامي"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)

            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDER,
                "تنبيه الاستعداد قبل الأذان",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تذكير لطيف قبل دخول وقت الصلاة بـ 10 دقائق للوضوء والاستعداد"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(reminderChannel)

            val followupChannel = NotificationChannel(
                CHANNEL_ID_FOLLOWUP,
                "متابعة أداء الصلاة بعد الأذان",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "يسألك بعد كل أذان بساعة «هل صليت؟» مع خيارَي نعم أو الآن سأصلي"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(followupChannel)

            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_ONGOING,
                "العد التنازلي الحي للصلاة القادمة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يعرض الوقت المتبقي للصلاة القادمة بهدوء في شريط الإشعارات"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    /**
     * Plays the takbeerat of the chosen imam's voice.
     */
    @Synchronized
    fun playVoice(context: Context, voice: AdhanVoice, onComplete: (() -> Unit)? = null) {
        stopAudio()
        try {
            val resId = voice.getAudioResId(context)
            if (resId == 0) {
                onComplete?.invoke()
                return
            }
            val mp = MediaPlayer.create(context.applicationContext, resId) ?: run {
                onComplete?.invoke()
                return
            }
            activeMediaPlayer = mp
            mp.setVolume(1.0f, 1.0f)
            mp.setOnCompletionListener {
                stopAudio()
                onComplete?.invoke()
            }
            mp.setOnErrorListener { _, _, _ ->
                stopAudio()
                onComplete?.invoke()
                true
            }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
            onComplete?.invoke()
        }
    }

    @Synchronized
    fun stopAudio() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeMediaPlayer = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            activeMediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shows phone notification and plays the Imam's voice saying "الله أكبر الله أكبر".
     */
    fun showAdhanNotification(
        context: Context,
        prayerNameAr: String,
        cityName: String,
        notificationId: Int
    ) {
        createNotificationChannel(context)

        val prefs = PreferencesManager(context)
        if (!prefs.isAdhanNotificationEnabled) return

        val voice = AdhanVoice.getById(prefs.selectedAdhanVoiceId)

        // Play the imam voice "الله أكبر الله أكبر"
        playVoice(context, voice)

        // Open App Intent
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Audio Action Intent
        val stopIntent = Intent(context, StopAdhanReceiver::class.java).apply {
            action = ACTION_STOP_ADHAN
            putExtra("notification_id", notificationId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 500,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = "الله أكبر، الله أكبر"
        val notificationText = if (prayerNameAr.isNotEmpty()) {
            "الله أكبر الله أكبر • حان موعد أذان $prayerNameAr"
        } else {
            "الله أكبر الله أكبر • تكبيرات الأذان"
        }

        val bigText = buildString {
            append("الله أكبر، الله أكبر\n")
            if (prayerNameAr.isNotEmpty()) {
                append("حان موعد أذان $prayerNameAr في $cityName\n")
            }
            append("صوت المؤذن: ${voice.nameAr}")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .addAction(
                android.R.drawable.ic_media_pause,
                "إيقاف الصوت",
                stopPendingIntent
            )

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
            e.printStackTrace()
        }
    }

    /**
     * Schedules alarms for all prayers in DayPrayerTimes that are in the future.
     */
    fun scheduleAllPrayers(context: Context, times: DayPrayerTimes, cityName: String) {
        val prefs = PreferencesManager(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()
        val prayersToSchedule = listOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        for (item in times.items) {
            if (item.type in prayersToSchedule) {
                val prayerEnabled = prefs.isPrayerNotificationEnabled(item.type.name)

                // 1. Main Adhan Alarm
                if (prefs.isAdhanNotificationEnabled && prayerEnabled && item.timestampMillis > now + 1000) {
                    val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        action = ACTION_PRAYER_ALARM
                        putExtra(EXTRA_PRAYER_TYPE, item.type.name)
                        putExtra(EXTRA_PRAYER_NAME, item.type.titleAr)
                        putExtra(EXTRA_CITY_NAME, cityName)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        item.type.ordinal,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    scheduleExactSafe(alarmManager, item.timestampMillis, pendingIntent)
                }

                // 2. Schedule 10-minute pre-prayer readiness reminder
                val tenMinsBefore = item.timestampMillis - 10 * 60 * 1000L
                if (prefs.isAdhanNotificationEnabled && prayerEnabled && prefs.isPrePrayerReminderEnabled && tenMinsBefore > now + 1000) {
                    val preIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        action = ACTION_PRE_PRAYER_ALARM
                        putExtra(EXTRA_PRAYER_TYPE, item.type.name)
                        putExtra(EXTRA_PRAYER_NAME, item.type.titleAr)
                        putExtra(EXTRA_CITY_NAME, cityName)
                    }
                    val prePendingIntent = PendingIntent.getBroadcast(
                        context,
                        item.type.ordinal + 200,
                        preIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    scheduleExactSafe(alarmManager, tenMinsBefore, prePendingIntent)
                }

                // 3. Schedule 1-hour post-prayer follow-up reminder ("هل صليت <الصلاة>؟")
                val followupIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = ACTION_POST_PRAYER_FOLLOWUP_ALARM
                    putExtra(EXTRA_PRAYER_TYPE, item.type.name)
                    putExtra(EXTRA_PRAYER_NAME, item.type.titleAr)
                    putExtra(EXTRA_CITY_NAME, cityName)
                    putExtra(EXTRA_IS_SNOOZE_RETRY, false)
                }
                val followupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    item.type.ordinal + 400,
                    followupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val oneHourAfter = item.timestampMillis + POST_PRAYER_FOLLOWUP_DELAY_MS
                if (prefs.isPostPrayerFollowupEnabled && prayerEnabled && oneHourAfter > now + 1000) {
                    scheduleExactSafe(alarmManager, oneHourAfter, followupPendingIntent)
                } else if (!prefs.isPostPrayerFollowupEnabled || !prayerEnabled) {
                    alarmManager.cancel(followupPendingIntent)
                }
            }
        }
    }

    private fun scheduleExactSafe(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun schedulePostPrayerFollowupSnooze(
        context: Context,
        prayerTypeStr: String,
        prayerNameAr: String,
        cityName: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val ordinal = try {
            PrayerType.valueOf(prayerTypeStr).ordinal
        } catch (_: Exception) {
            0
        }
        val snoozeIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_POST_PRAYER_FOLLOWUP_ALARM
            putExtra(EXTRA_PRAYER_TYPE, prayerTypeStr)
            putExtra(EXTRA_PRAYER_NAME, prayerNameAr)
            putExtra(EXTRA_CITY_NAME, cityName)
            putExtra(EXTRA_IS_SNOOZE_RETRY, true)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            ordinal + 450,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactSafe(alarmManager, System.currentTimeMillis() + POST_PRAYER_SNOOZE_DELAY_MS, snoozePendingIntent)
    }

    fun buildPostPrayerFollowupNotification(
        context: Context,
        prayerTypeStr: String,
        prayerNameAr: String,
        cityName: String,
        notificationId: Int,
        isSnoozeRetry: Boolean = false
    ): android.app.Notification {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val yesIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYED_YES
            putExtra(EXTRA_PRAYER_TYPE, prayerTypeStr)
            putExtra(EXTRA_PRAYER_NAME, prayerNameAr)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val yesPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            yesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soonIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYED_SOON
            putExtra(EXTRA_PRAYER_TYPE, prayerTypeStr)
            putExtra(EXTRA_PRAYER_NAME, prayerNameAr)
            putExtra(EXTRA_CITY_NAME, cityName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_IS_SNOOZE_RETRY, isSnoozeRetry)
        }
        val soonPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            soonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "هل صليت $prayerNameAr؟ 🕌"
        val body = if (isSnoozeRetry) {
            "تذكير لطيف بأداء صلاة $prayerNameAr الآن في وقتها — تقبّل الله منك 🤍"
        } else {
            val locationSuffix = if (cityName.isNotBlank()) " في $cityName" else ""
            "مضت ساعة على أذان $prayerNameAr$locationSuffix — حافظ على صلاتك"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_FOLLOWUP)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                "نعم، صليت ✓",
                yesPendingIntent
            )
            .addAction(
                0,
                "لا، الآن سوف أُصلي",
                soonPendingIntent
            )
            .build()
    }

    fun showPostPrayerFollowupNotification(
        context: Context,
        prayerTypeStr: String,
        prayerNameAr: String,
        cityName: String,
        notificationId: Int,
        isSnoozeRetry: Boolean = false
    ) {
        val notification = buildPostPrayerFollowupNotification(
            context = context,
            prayerTypeStr = prayerTypeStr,
            prayerNameAr = prayerNameAr,
            cityName = cityName,
            notificationId = notificationId,
            isSnoozeRetry = isSnoozeRetry
        )
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showBriefFollowupConfirmation(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        createNotificationChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_FOLLOWUP)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(4500L)
            .setContentIntent(contentPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showPrePrayerReminderNotification(
        context: Context,
        prayerNameAr: String,
        cityName: String,
        notificationId: Int
    ) {
        createNotificationChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اقترب موعد صلاة $prayerNameAr 🕌")
            .setContentText("متبقٍ 10 دقائق على الأذان في $cityName — حان وقت الاستعداد والوضوء.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun updateOngoingCountdownNotification(
        context: Context,
        nextPrayerName: String,
        nextPrayerTimeFormatted: String,
        remainingMillis: Long,
        cityName: String
    ) {
        val prefs = PreferencesManager(context)
        if (!prefs.isOngoingCountdownEnabled || remainingMillis <= 1000L) {
            cancelOngoingCountdownNotification(context)
            return
        }
        createNotificationChannel(context)

        val totalMinutes = (remainingMillis / 60000L).coerceAtLeast(0L).toInt()
        val hrs = totalMinutes / 60
        val mins = totalMinutes % 60
        val remainingStr = when {
            hrs > 0 && mins > 0 -> "$hrs ساعة و $mins دقيقة"
            hrs > 0 -> "$hrs ساعة"
            mins > 0 -> "$mins دقيقة"
            else -> "أقل من دقيقة"
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ONGOING,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ONGOING
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_ONGOING + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("الصلاة القادمة: $nextPrayerName ($nextPrayerTimeFormatted)")
            .setContentText("المتبقي: $remainingStr • $cityName")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(remainingMillis.coerceAtLeast(1000L))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(0, "إخفاء", dismissPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ONGOING, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelOngoingCountdownNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ONGOING)
        } catch (_: Exception) {
        }
    }
}
