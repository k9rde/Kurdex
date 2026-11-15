package com.example

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.PrayerType
import com.example.util.AdhanNotificationHelper
import com.example.util.PrayerAlarmReceiver
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PostPrayerFollowupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun buildPostPrayerFollowupNotification_containsArabicQuestionAndTwoActions() {
        val notification = AdhanNotificationHelper.buildPostPrayerFollowupNotification(
            context = context,
            prayerTypeStr = PrayerType.DHUHR.name,
            prayerNameAr = PrayerType.DHUHR.titleAr,
            cityName = "الموصل",
            notificationId = 402,
            isSnoozeRetry = false
        )

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        assertTrue("Expected question title with Dhuhr, got: $title", title.contains("هل صليت الظهر"))

        val actions = notification.actions
        assertNotNull(actions)
        assertEquals(2, actions.size)
        assertEquals("نعم، صليت ✓", actions[0].title.toString())
        assertEquals("لا، الآن سوف أُصلي", actions[1].title.toString())
    }

    @Test
    fun receiverActionPrayedYes_marksPrayerCompletedInPreferences() {
        val prefs = PreferencesManager(context)
        val todayKey = prefs.getTodayDateKey()
        assertFalse(prefs.getCompletedPrayersForDate(todayKey).contains(PrayerType.ASR.name))

        val receiver = PrayerAlarmReceiver()
        val yesIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = AdhanNotificationHelper.ACTION_PRAYED_YES
            putExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE, PrayerType.ASR.name)
            putExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME, PrayerType.ASR.titleAr)
            putExtra(AdhanNotificationHelper.EXTRA_NOTIFICATION_ID, 403)
        }

        receiver.onReceive(context, yesIntent)

        val completed = prefs.getCompletedPrayersForDate(todayKey)
        assertTrue("Expected ASR to be marked completed after tapping Yes", completed.contains(PrayerType.ASR.name))
    }

    @Test
    fun ongoingCountdownNotification_isCanceledWhenAdhanArrivesOrRemainingIsZero() {
        val prefs = PreferencesManager(context)
        prefs.isOngoingCountdownEnabled = true

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)

        // Post ongoing countdown with 5 minutes remaining
        AdhanNotificationHelper.updateOngoingCountdownNotification(
            context = context,
            nextPrayerName = "الظهر",
            nextPrayerTimeFormatted = "12:05 م",
            remainingMillis = 5 * 60_000L,
            cityName = "الموصل"
        )
        val activeBefore = shadowNm.getNotification(AdhanNotificationHelper.NOTIFICATION_ID_ONGOING)
        assertNotNull("Ongoing notification should exist before prayer time", activeBefore)
        assertTrue("Ongoing notification should set timeoutAfter to auto-expire at prayer time", activeBefore!!.timeoutAfter > 0L)

        // Simulate Adhan alarm arriving
        val receiver = PrayerAlarmReceiver()
        val adhanIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = AdhanNotificationHelper.ACTION_PRAYER_ALARM
            putExtra(AdhanNotificationHelper.EXTRA_PRAYER_TYPE, PrayerType.DHUHR.name)
            putExtra(AdhanNotificationHelper.EXTRA_PRAYER_NAME, PrayerType.DHUHR.titleAr)
            putExtra(AdhanNotificationHelper.EXTRA_CITY_NAME, "الموصل")
        }
        receiver.onReceive(context, adhanIntent)

        assertNull(
            "Ongoing countdown notification must disappear once prayer time arrives",
            shadowNm.getNotification(AdhanNotificationHelper.NOTIFICATION_ID_ONGOING)
        )
    }
}
