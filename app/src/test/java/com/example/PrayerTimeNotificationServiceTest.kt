package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.PrayerType
import com.example.service.PrayerTimeNotificationService
import com.example.util.AdhanNotificationHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrayerTimeNotificationServiceTest {

    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val shadowApp = Shadows.shadowOf(application)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun serviceCreation_registersNotificationChannelsAndTransitionsLifecycleCleanly() {
        val controller = Robolectric.buildService(PrayerTimeNotificationService::class.java)
            .create()
            .startCommand(0, 0)
        val service = controller.get()
        assertNotNull(service)

        val serviceChannel =
            notificationManager.getNotificationChannel(PrayerTimeNotificationService.CHANNEL_ID_SERVICE)
        assertNotNull("Background service notification channel must be registered", serviceChannel)

        val adhanChannel =
            notificationManager.getNotificationChannel(AdhanNotificationHelper.CHANNEL_ID)
        assertNotNull("Adhan alert notification channel must be registered", adhanChannel)

        val stopIntent = Intent(application, PrayerTimeNotificationService::class.java).apply {
            action = PrayerTimeNotificationService.ACTION_STOP_MONITORING
        }
        Robolectric.buildService(PrayerTimeNotificationService::class.java, stopIntent)
            .create()
            .startCommand(0, 0)
            .destroy()
        controller.destroy()
    }

    @Test
    fun resolveCurrentLocationAndTimes_usesGrantedLocationManagerCoordinates() {
        val shadowApp = Shadows.shadowOf(application)
        shadowApp.grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val locationManager =
            application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = Shadows.shadowOf(locationManager)

        // Baghdad coordinates (~33.3152 N, 44.3661 E) different from default Mosul
        val baghdadLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 33.3152
            longitude = 44.3661
            accuracy = 8f
            time = System.currentTimeMillis()
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, baghdadLocation)

        val resolved = PrayerTimeNotificationService.resolveCurrentLocationAndTimes(application)
        assertEquals(33.3152, resolved.latitude, 0.0001)
        assertEquals(44.3661, resolved.longitude, 0.0001)
        assertEquals("موقعي الحالي (GPS)", resolved.locationLabel)
        assertTrue("Calculated items must include all 6 daily prayer markers", resolved.prayerTimes.items.size == 6)
    }

    @Test
    fun evaluateAndNotifyAtPrayerTime_notifiesOnceViaNotificationManagerWhenPrayerArrives() {
        val prefs = PreferencesManager(application)
        prefs.isAdhanNotificationEnabled = true

        val resolved = PrayerTimeNotificationService.resolveCurrentLocationAndTimes(application)
        val dhuhrItem = resolved.prayerTimes.items.first { it.type == PrayerType.DHUHR }

        // Ensure alerts are off slightly before Dhuhr
        val beforeDhuhr = PrayerTimeNotificationService.evaluateAndNotifyAtPrayerTime(
            context = application,
            nowMillis = dhuhrItem.timestampMillis - 60_000L,
            resolved = resolved,
            notificationManager = notificationManager
        )
        assertNull("Should not notify before calculated prayer time", beforeDhuhr)

        // Trigger at exact calculated Dhuhr time + 5 seconds
        val fired = PrayerTimeNotificationService.evaluateAndNotifyAtPrayerTime(
            context = application,
            nowMillis = dhuhrItem.timestampMillis + 5_000L,
            resolved = resolved,
            notificationManager = notificationManager
        )
        assertNotNull("Expected NotificationManager alert at calculated Dhuhr time", fired)
        assertEquals(PrayerType.DHUHR, fired?.type)

        val shadowNm = Shadows.shadowOf(notificationManager)
        val expectedId =
            PrayerTimeNotificationService.NOTIFICATION_ID_ALERT_BASE + PrayerType.DHUHR.ordinal
        val postedNotification = shadowNm.getNotification(expectedId)
        assertNotNull("Posted notification must exist on NotificationManager", postedNotification)

        // Re-evaluating within the same alert window must not send a duplicate alert
        val duplicate = PrayerTimeNotificationService.evaluateAndNotifyAtPrayerTime(
            context = application,
            nowMillis = dhuhrItem.timestampMillis + 35_000L,
            resolved = resolved,
            notificationManager = notificationManager
        )
        assertNull("Duplicate alert for the same prayer and day must be suppressed", duplicate)
    }

    @Test
    fun syncServiceState_onlyStartsPersistentServiceWhenUserEnablesOngoingCountdown() {
        val prefs = PreferencesManager(application)
        val shadowApp = Shadows.shadowOf(application)

        // Default is false: persistent ongoing notification service must NOT start automatically
        prefs.isAdhanNotificationEnabled = true
        prefs.isOngoingCountdownEnabled = false
        shadowApp.clearStartedServices()
        PrayerTimeNotificationService.syncServiceState(application)
        assertNull(
            "Persistent foreground notification service must stay off by default",
            shadowApp.nextStartedService
        )

        // When user turns on the Settings toggle, persistent notification service starts
        prefs.isOngoingCountdownEnabled = true
        PrayerTimeNotificationService.syncServiceState(application)
        val startedIntent = shadowApp.nextStartedService
        assertNotNull("Service should start when user enables ongoing countdown", startedIntent)
        assertEquals(
            PrayerTimeNotificationService::class.java.name,
            startedIntent?.component?.className
        )
    }
}
