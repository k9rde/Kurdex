package com.example.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.PreferencesManager
import com.example.service.PrayerTimeNotificationService
import java.util.concurrent.TimeUnit

/**
 * Configures and schedules background prayer time synchronization using [WorkManager] with
 * network connectivity constraints and location-change detection triggers.
 */
object PrayerSyncWorkScheduler {

    const val UNIQUE_PERIODIC_WORK_NAME = "periodic_prayer_times_sync"
    const val UNIQUE_LOCATION_SYNC_WORK_NAME = "location_changed_prayer_times_sync"
    const val TAG_PRAYER_SYNC = "prayer_times_background_sync"
    const val TAG_LOCATION_SYNC = "prayer_times_location_change_sync"

    const val PERIODIC_INTERVAL_HOURS = 6L
    const val FLEX_INTERVAL_HOURS = 1L
    const val SIGNIFICANT_LOCATION_CHANGE_METERS = 3_000f
    private const val LOCATION_MONITOR_MIN_TIME_MS = 15 * 60 * 1000L
    private const val LOCATION_MONITOR_REQUEST_CODE = 6101

    /**
     * Constraints enforcing network connectivity (and conserving battery) before WorkManager
     * runs background prayer time updates.
     */
    fun buildNetworkConstraints(requireBatteryNotLow: Boolean = true): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(requireBatteryNotLow)
            .build()
    }

    /**
     * Builds the [PeriodicWorkRequest] that refreshes prayer times every 6 hours (with a 1-hour
     * flex window) whenever network connectivity is available.
     */
    fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<PrayerTimesSyncWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS,
            FLEX_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(buildNetworkConstraints(requireBatteryNotLow = true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    PrayerTimesSyncWorker.KEY_TRIGGER_REASON to PrayerTimesSyncWorker.REASON_PERIODIC
                )
            )
            .addTag(TAG_PRAYER_SYNC)
            .build()
    }

    /**
     * Builds a network-constrained [OneTimeWorkRequest] triggered immediately when a significant
     * location change (>= 3 km) occurs.
     */
    fun buildLocationChangedWorkRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<PrayerTimesSyncWorker>()
            .setConstraints(buildNetworkConstraints(requireBatteryNotLow = false))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    PrayerTimesSyncWorker.KEY_TRIGGER_REASON to
                        PrayerTimesSyncWorker.REASON_LOCATION_CHANGED,
                    PrayerTimesSyncWorker.KEY_FORCE_REFRESH to true
                )
            )
            .addTag(TAG_PRAYER_SYNC)
            .addTag(TAG_LOCATION_SYNC)
            .build()
    }

    fun schedulePeriodicSync(context: Context) {
        try {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                buildPeriodicWorkRequest()
            )
        } catch (_: Exception) {
        }
        registerPassiveLocationChangeMonitor(context)
    }

    fun enqueueLocationChangedSync(context: Context) {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_LOCATION_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildLocationChangedWorkRequest()
            )
        } catch (_: Exception) {
        }
    }

    fun onLocationUpdated(context: Context, latitude: Double, longitude: Double) {
        val prefs = PreferencesManager(context)
        if (!prefs.isAutoSyncOnlineEnabled) return

        val prevLat = prefs.lastSyncedLatitude
        val prevLng = prefs.lastSyncedLongitude
        val movedFarEnough = if (prevLat == null || prevLng == null) {
            true
        } else {
            PrayerTimesSyncWorker.calculateDistanceMeters(prevLat, prevLng, latitude, longitude) >=
                SIGNIFICANT_LOCATION_CHANGE_METERS
        }

        if (movedFarEnough) {
            enqueueLocationChangedSync(context)
        }
    }

    fun cancelPeriodicSync(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(UNIQUE_LOCATION_SYNC_WORK_NAME)
        } catch (_: Exception) {
        }
        unregisterPassiveLocationChangeMonitor(context)
    }

    fun syncWorkState(context: Context) {
        val prefs = PreferencesManager(context)
        if (prefs.isAutoSyncOnlineEnabled) {
            schedulePeriodicSync(context)
        } else {
            cancelPeriodicSync(context)
        }
    }

    fun registerPassiveLocationChangeMonitor(context: Context) {
        if (!PrayerTimeNotificationService.hasLocationPermission(context)) return
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val pendingIntent = buildLocationPendingIntent(context)
        try {
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    LOCATION_MONITOR_MIN_TIME_MS,
                    SIGNIFICANT_LOCATION_CHANGE_METERS,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun unregisterPassiveLocationChangeMonitor(context: Context) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            locationManager.removeUpdates(buildLocationPendingIntent(context))
        } catch (_: Exception) {
        }
    }

    private fun buildLocationPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PrayerLocationChangeReceiver::class.java).apply {
            action = PrayerLocationChangeReceiver.ACTION_LOCATION_CHANGED
        }
        return PendingIntent.getBroadcast(
            context,
            LOCATION_MONITOR_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
