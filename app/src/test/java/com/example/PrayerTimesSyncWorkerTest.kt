package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.data.PreferencesManager
import com.example.worker.PrayerLocationChangeReceiver
import com.example.worker.PrayerSyncWorkScheduler
import com.example.worker.PrayerTimesSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrayerTimesSyncWorkerTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun schedulePeriodicSync_enqueuesUniquePeriodicWorkWithNetworkConstraint() {
        val prefs = PreferencesManager(context)
        prefs.isAutoSyncOnlineEnabled = true

        PrayerSyncWorkScheduler.schedulePeriodicSync(context)

        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!

        val workInfosBefore = workManager
            .getWorkInfosForUniqueWork(PrayerSyncWorkScheduler.UNIQUE_PERIODIC_WORK_NAME)
            .get()
        assertEquals(1, workInfosBefore.size)

        val periodicInfo = workInfosBefore.first()
        assertEquals(WorkInfo.State.ENQUEUED, periodicInfo.state)
        assertEquals(
            NetworkType.CONNECTED,
            periodicInfo.constraints.requiredNetworkType
        )
        assertTrue(periodicInfo.constraints.requiresBatteryNotLow())
        assertTrue(periodicInfo.tags.contains(PrayerSyncWorkScheduler.TAG_PRAYER_SYNC))

        // Simulate constraints + period delay being satisfied
        testDriver.setAllConstraintsMet(periodicInfo.id)
        testDriver.setPeriodDelayMet(periodicInfo.id)

        val workInfoAfter = workManager.getWorkInfoById(periodicInfo.id).get()
        assertNotNull(workInfoAfter)
        assertEquals(WorkInfo.State.ENQUEUED, workInfoAfter!!.state)
    }

    @Test
    fun onLocationUpdated_enqueuesImmediateConstrainedWorkOnlyWhenLocationMovesSignificantly() {
        val prefs = PreferencesManager(context)
        prefs.isAutoSyncOnlineEnabled = true
        // Anchor synced location at Mosul (36.3489, 43.1577)
        prefs.recordSyncedLocationAndDate(36.3489, 43.1577, "2026_11_15")

        val workManager = WorkManager.getInstance(context)

        // Small shift (~200 m) must NOT trigger a location-change sync
        PrayerSyncWorkScheduler.onLocationUpdated(context, 36.3502, 43.1585)
        val afterSmallMove = workManager
            .getWorkInfosForUniqueWork(PrayerSyncWorkScheduler.UNIQUE_LOCATION_SYNC_WORK_NAME)
            .get()
        assertTrue(
            "Movement under 3 km should not enqueue location sync",
            afterSmallMove.isEmpty()
        )

        // Significant shift (~350 km to Baghdad) MUST enqueue network-constrained sync
        val receiver = PrayerLocationChangeReceiver()
        val locationIntent = Intent(PrayerLocationChangeReceiver.ACTION_LOCATION_CHANGED).apply {
            putExtra(
                LocationManager.KEY_LOCATION_CHANGED,
                Location(LocationManager.PASSIVE_PROVIDER).apply {
                    latitude = 33.3152
                    longitude = 44.3661
                    time = System.currentTimeMillis()
                }
            )
        }
        receiver.onReceive(context, locationIntent)

        val afterLargeMove = workManager
            .getWorkInfosForUniqueWork(PrayerSyncWorkScheduler.UNIQUE_LOCATION_SYNC_WORK_NAME)
            .get()
        assertEquals(1, afterLargeMove.size)
        val locWorkInfo = afterLargeMove.first()
        assertEquals(NetworkType.CONNECTED, locWorkInfo.constraints.requiredNetworkType)
        assertTrue(locWorkInfo.tags.contains(PrayerSyncWorkScheduler.TAG_LOCATION_SYNC))
    }

    @Test
    fun cancelPeriodicSync_cancelsScheduledWorkWhenDisabled() {
        val prefs = PreferencesManager(context)
        prefs.isAutoSyncOnlineEnabled = true
        PrayerSyncWorkScheduler.schedulePeriodicSync(context)

        prefs.isAutoSyncOnlineEnabled = false
        PrayerSyncWorkScheduler.syncWorkState(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PrayerSyncWorkScheduler.UNIQUE_PERIODIC_WORK_NAME)
            .get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun prayerTimesSyncWorker_executesCleanlyWithAstronomicalFallbackOffline() = runBlocking {
        val prefs = PreferencesManager(context)
        prefs.isAutoSyncOnlineEnabled = true

        val worker = TestListenableWorkerBuilder<PrayerTimesSyncWorker>(context)
            .setInputData(
                workDataOf(
                    PrayerTimesSyncWorker.KEY_TRIGGER_REASON to PrayerTimesSyncWorker.REASON_PERIODIC
                )
            )
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val out = (result as ListenableWorker.Result.Success).outputData
        assertEquals("updated_astronomical", out.getString(PrayerTimesSyncWorker.KEY_OUT_STATUS))
        assertFalse(out.getString(PrayerTimesSyncWorker.KEY_OUT_LOCATION).isNullOrBlank())
    }
}
