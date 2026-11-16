package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.model.AdhanVoice
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.DayPrayerTimes
import com.example.model.JuristicMethod
import com.example.model.PrayerItem
import com.example.model.PrayerType
import com.example.util.AdhanNotificationHelper
import com.example.util.PrayerAlarmReceiver
import com.example.util.PrayerCalculator
import com.example.util.StopAdhanReceiver
import com.example.widget.PrayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Foreground background service that tracks the user's current location (GPS/Network when permitted,
 * or saved city fallback), calculates daily prayer times via [PrayerCalculator], and uses
 * [NotificationManager] directly to alert the user at each calculated prayer time.
 */
class PrayerTimeNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    data class ResolvedPrayerContext(
        val locationLabel: String,
        val latitude: Double,
        val longitude: Double,
        val timezoneOffsetHours: Double,
        val prayerTimes: DayPrayerTimes
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                monitorJob?.cancel()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_AND_NOTIFY -> {
                promoteToForeground(System.currentTimeMillis())
                serviceScope.launch {
                    evaluateAndNotifyAtPrayerTime(this@PrayerTimeNotificationService, System.currentTimeMillis())
                }
                startMonitoringLoop()
            }
            else -> {
                promoteToForeground(System.currentTimeMillis())
                startMonitoringLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(nowMillis: Long) {
        val notification = buildServiceStatusNotification(this, nowMillis)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_SERVICE, notification)
            }
        } catch (_: Exception) {
            // Safely handle restricted foreground start contexts on newer Android OS versions
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    private fun startMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            var lastScheduledDayKey = ""
            while (isActive) {
                val now = System.currentTimeMillis()
                val resolved = resolveCurrentLocationAndTimes(this@PrayerTimeNotificationService, now)
                val notificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                // Update foreground notification with the latest calculated next prayer
                if (canPostNotifications(this@PrayerTimeNotificationService)) {
                    try {
                        notificationManager.notify(
                            NOTIFICATION_ID_SERVICE,
                            buildServiceStatusNotification(this@PrayerTimeNotificationService, now, resolved)
                        )
                    } catch (_: SecurityException) {
                    }
                }

                // Evaluate if any calculated prayer time has just arrived and dispatch alert via NotificationManager
                evaluateAndNotifyAtPrayerTime(
                    context = this@PrayerTimeNotificationService,
                    nowMillis = now,
                    resolved = resolved,
                    notificationManager = notificationManager
                )

                // Re-sync exact alarm backups & home screen widgets once per day or after location change
                val dayKey = formatDateKey(now, resolved.timezoneOffsetHours)
                if (dayKey != lastScheduledDayKey) {
                    lastScheduledDayKey = dayKey
                    AdhanNotificationHelper.scheduleAllPrayers(
                        this@PrayerTimeNotificationService,
                        resolved.prayerTimes,
                        resolved.locationLabel
                    )
                    PrayerWidgetProvider.updateAllWidgets(this@PrayerTimeNotificationService)
                }

                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    companion object {
        const val ACTION_START_MONITORING = "com.example.service.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.service.ACTION_STOP_MONITORING"
        const val ACTION_CHECK_AND_NOTIFY = "com.example.service.ACTION_CHECK_AND_NOTIFY"

        const val CHANNEL_ID_SERVICE = "prayer_background_service_channel"
        const val NOTIFICATION_ID_SERVICE = 901
        const val NOTIFICATION_ID_ALERT_BASE = 1100

        private const val CHECK_INTERVAL_MS = 30_000L
        private const val ALERT_WINDOW_MS = 90_000L
        private const val PREFS_ALERT_DEDUP = "prayer_service_alert_dedup"

        private val ALERTABLE_PRAYERS = setOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        fun startMonitoring(context: Context) {
            val intent = Intent(context, PrayerTimeNotificationService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // On Android 12+ background start restrictions may disallow foreground service launch
                // outside an active foreground window; exact AlarmManager alarms remain scheduled.
            }
        }

        fun stopMonitoring(context: Context) {
            try {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID_SERVICE)
            } catch (_: Exception) {
            }
            val intent = Intent(context, PrayerTimeNotificationService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }

        fun syncServiceState(context: Context) {
            val prefs = PreferencesManager(context)
            if (prefs.isOngoingCountdownEnabled) {
                startMonitoring(context)
            } else {
                stopMonitoring(context)
            }
        }

        fun ensureNotificationChannels(context: Context) {
            AdhanNotificationHelper.createNotificationChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val serviceChannel = NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    "خدمة متابعة مواقيت الصلاة",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "تحديث مواقيت الصلاة حسب موقعك الحالي وتنبيهك عند دخول وقت كل صلاة"
                    setShowBadge(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(serviceChannel)
            }
        }

        fun canPostNotifications(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun getBestLastKnownLocation(context: Context): Location? {
            if (!hasLocationPermission(context)) return null
            val locationManager =
                context.getSystemService(LOCATION_SERVICE) as? LocationManager ?: return null

            val providers = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
            }

            var best: Location? = null
            for (provider in providers) {
                val loc = try {
                    locationManager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: Exception) {
                    null
                } ?: continue

                if (loc.latitude == 0.0 && loc.longitude == 0.0) continue
                if (best == null) {
                    best = loc
                } else {
                    val timeDelta = loc.time - best.time
                    val significantlyNewer = timeDelta > 120_000L
                    val significantlyOlder = timeDelta < -120_000L
                    val moreAccurate = loc.hasAccuracy() &&
                        (!best.hasAccuracy() || loc.accuracy < best.accuracy)
                    if (significantlyNewer || (!significantlyOlder && moreAccurate)) {
                        best = loc
                    }
                }
            }
            return best
        }

        fun resolveCurrentLocationAndTimes(
            context: Context,
            nowMillis: Long = System.currentTimeMillis()
        ): ResolvedPrayerContext {
            val prefs = PreferencesManager(context)
            val savedCity = prefs.getSelectedCity()
            val liveLocation = getBestLastKnownLocation(context)

            val lat: Double
            val lng: Double
            val tzHours: Double
            val locationLabel: String

            if (liveLocation != null) {
                lat = liveLocation.latitude
                lng = liveLocation.longitude
                tzHours = TimeZone.getDefault().getOffset(nowMillis) / 3_600_000.0
                val movedSignificantly = abs(lat - savedCity.latitude) > 0.08 ||
                    abs(lng - savedCity.longitude) > 0.08
                locationLabel = if (!movedSignificantly) {
                    savedCity.nameAr.ifEmpty { savedCity.name }
                } else {
                    "موقعي الحالي (GPS)"
                }
            } else {
                lat = savedCity.latitude
                lng = savedCity.longitude
                tzHours = savedCity.timezone
                locationLabel = savedCity.nameAr.ifEmpty { savedCity.name }
            }

            val cal = Calendar.getInstance(PrayerCalculator.getTimeZoneForOffset(tzHours)).apply {
                timeInMillis = nowMillis
            }
            val calcMethod = CalculationMethod.entries.getOrElse(prefs.calculationMethodIndex) {
                CalculationMethod.MWL
            }
            val juristicMethod = JuristicMethod.entries.getOrElse(prefs.juristicMethodIndex) {
                JuristicMethod.STANDARD
            }

            val times = PrayerCalculator.calculateTimes(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
                lat = lat,
                lng = lng,
                timeZone = tzHours,
                method = calcMethod,
                juristic = juristicMethod,
                fajrOffset = prefs.getPrayerOffset("fajr"),
                sunriseOffset = prefs.getPrayerOffset("sunrise"),
                dhuhrOffset = prefs.getPrayerOffset("dhuhr"),
                asrOffset = prefs.getPrayerOffset("asr"),
                maghribOffset = prefs.getPrayerOffset("maghrib"),
                ishaOffset = prefs.getPrayerOffset("isha")
            )

            return ResolvedPrayerContext(
                locationLabel = locationLabel,
                latitude = lat,
                longitude = lng,
                timezoneOffsetHours = tzHours,
                prayerTimes = times
            )
        }

        fun buildServiceStatusNotification(
            context: Context,
            nowMillis: Long = System.currentTimeMillis(),
            resolved: ResolvedPrayerContext = resolveCurrentLocationAndTimes(context, nowMillis)
        ): Notification {
            ensureNotificationChannels(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_SERVICE,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextPrayer = resolved.prayerTimes.nextPrayer
            val title = if (nextPrayer != null) {
                "الصلاة القادمة: ${nextPrayer.type.titleAr} (${nextPrayer.timeFormatted})"
            } else {
                "متابعة مواقيت الصلاة مفعّلة"
            }

            val remainingText = if (nextPrayer != null) {
                val remMillis = (nextPrayer.timestampMillis - nowMillis).coerceAtLeast(0L)
                val totalMins = (remMillis / 60_000L).toInt()
                val hrs = totalMins / 60
                val mins = totalMins % 60
                when {
                    hrs > 0 && mins > 0 -> "المتبقي $hrs س و $mins د"
                    hrs > 0 -> "المتبقي $hrs ساعة"
                    mins > 0 -> "المتبقي $mins دقيقة"
                    else -> "اقترب موعد الأذان"
                }
            } else {
                "حساب مواقيت الصلاة حسب موقعك"
            }

            val dismissIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = AdhanNotificationHelper.ACTION_DISMISS_ONGOING
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_SERVICE + 1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("$remainingText • ${resolved.locationLabel}")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPendingIntent)
                .addAction(0, "إخفاء الإشعار", dismissPendingIntent)
                .build()
        }

        /**
         * Checks whether any calculated prayer time falls within `[timestampMillis, timestampMillis + ALERT_WINDOW_MS]`
         * at [nowMillis]. If so (and not already dispatched for today's date), dispatches an alert via
         * [NotificationManager] and returns the notified [PrayerItem] (or null if none triggered).
         */
        fun evaluateAndNotifyAtPrayerTime(
            context: Context,
            nowMillis: Long = System.currentTimeMillis(),
            resolved: ResolvedPrayerContext = resolveCurrentLocationAndTimes(context, nowMillis),
            notificationManager: NotificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ): PrayerItem? {
            ensureNotificationChannels(context)
            val prefs = PreferencesManager(context)
            if (!prefs.isAdhanNotificationEnabled) return null

            val dateKey = formatDateKey(nowMillis, resolved.timezoneOffsetHours)
            val dedupPrefs = context.getSharedPreferences(PREFS_ALERT_DEDUP, MODE_PRIVATE)

            for (item in resolved.prayerTimes.items) {
                if (item.type !in ALERTABLE_PRAYERS) continue
                if (!prefs.isPrayerNotificationEnabled(item.type.name)) continue

                val elapsedSincePrayer = nowMillis - item.timestampMillis
                if (elapsedSincePrayer in 0L..ALERT_WINDOW_MS) {
                    val dedupKey = "${dateKey}_${item.type.name}"
                    if (dedupPrefs.getBoolean(dedupKey, false)) {
                        return null
                    }
                    dedupPrefs.edit().putBoolean(dedupKey, true).apply()

                    val notificationId = NOTIFICATION_ID_ALERT_BASE + item.type.ordinal
                    val voice = AdhanVoice.getById(prefs.selectedAdhanVoiceId)
                    AdhanNotificationHelper.cancelOngoingCountdownNotification(context)
                    AdhanNotificationHelper.playVoice(context, voice)

                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPendingIntent = PendingIntent.getActivity(
                        context,
                        notificationId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val stopIntent = Intent(context, StopAdhanReceiver::class.java).apply {
                        action = AdhanNotificationHelper.ACTION_STOP_ADHAN
                        putExtra("notification_id", notificationId)
                    }
                    val stopPendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId + 50,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val title = "حان الآن موعد صلاة ${item.type.titleAr}"
                    val summary = "${item.timeFormatted} • ${resolved.locationLabel}"
                    val bigBody = "الله أكبر، الله أكبر\nحان الآن موعد أذان ${item.type.titleAr} (${item.timeFormatted}) في ${resolved.locationLabel}\nصوت المؤذن: ${voice.nameAr}"

                    val alertNotification = NotificationCompat.Builder(context, AdhanNotificationHelper.CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(summary)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(openPendingIntent)
                        .setAutoCancel(true)
                        .setVibrate(longArrayOf(0, 500, 300, 500))
                        .addAction(android.R.drawable.ic_media_pause, "إيقاف الصوت", stopPendingIntent)
                        .build()

                    if (canPostNotifications(context)) {
                        try {
                            notificationManager.notify(notificationId, alertNotification)
                        } catch (_: SecurityException) {
                        }
                    }

                    PrayerWidgetProvider.updateAllWidgets(context)
                    return item
                }
            }
            return null
        }

        fun markPrayerNotified(
            context: Context,
            prayerType: PrayerType,
            nowMillis: Long = System.currentTimeMillis(),
            tzHours: Double = PreferencesManager(context).getSelectedCity().timezone
        ) {
            val dateKey = formatDateKey(nowMillis, tzHours)
            val dedupPrefs = context.getSharedPreferences(PREFS_ALERT_DEDUP, MODE_PRIVATE)
            dedupPrefs.edit().putBoolean("${dateKey}_${prayerType.name}", true).apply()
        }

        private fun formatDateKey(nowMillis: Long, tzHours: Double): String {
            val cal = Calendar.getInstance(PrayerCalculator.getTimeZoneForOffset(tzHours)).apply {
                timeInMillis = nowMillis
            }
            return "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH) + 1}_${cal.get(Calendar.DAY_OF_MONTH)}"
        }
    }
}
