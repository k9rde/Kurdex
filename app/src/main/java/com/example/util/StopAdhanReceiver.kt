package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class StopAdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AdhanNotificationHelper.stopAudio()
        val notifId = intent.getIntExtra("notification_id", -1)
        if (notifId != -1) {
            try {
                NotificationManagerCompat.from(context).cancel(notifId)
            } catch (_: Exception) {}
        }
    }
}
