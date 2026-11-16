package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build

/**
 * Receives passive system location change broadcasts when the device moves significantly (>= 3 km)
 * and dispatches a network-constrained WorkManager sync via [PrayerSyncWorkScheduler].
 */
class PrayerLocationChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCATION_CHANGED) return

        val location: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)
        }

        if (location != null && (location.latitude != 0.0 || location.longitude != 0.0)) {
            PrayerSyncWorkScheduler.onLocationUpdated(
                context = context,
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }

    companion object {
        const val ACTION_LOCATION_CHANGED = "com.example.worker.ACTION_LOCATION_CHANGED"
    }
}
