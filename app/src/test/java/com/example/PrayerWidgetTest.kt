package com.example

import android.content.Context
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.PrayerType
import com.example.widget.PrayerWidgetProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrayerWidgetTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun widgetRemoteViews_inflatesCleanlyWithTopCenterDigitalCountdownAndAllSixPrayers() {
        val prefs = PreferencesManager(context)
        val todayKey = prefs.getTodayDateKey()
        prefs.markPrayerCompleted(todayKey, PrayerType.FAJR.name)
        prefs.markPrayerCompleted(todayKey, PrayerType.DHUHR.name)

        val (remoteViews, nextBoundary) = PrayerWidgetProvider.buildWidgetRemoteViews(context)
        assertNotNull("Next prayer boundary timestamp should be scheduled", nextBoundary)

        val host = FrameLayout(context)
        val inflatedView: View = remoteViews.apply(context, host)
        assertNotNull(inflatedView)

        val chronometer = inflatedView.findViewById<Chronometer>(R.id.widget_countdown_chronometer)
        assertNotNull("Top-center Chronometer must exist", chronometer)
        assertTrue("Chronometer must be in countdown mode", chronometer.isCountDown)

        val countdownLabel = inflatedView.findViewById<TextView>(R.id.widget_next_prayer_label)
        assertTrue(
            "Top-center label must describe remaining time: ${countdownLabel.text}",
            countdownLabel.text.toString().startsWith("المتبقي ")
        )

        val progressBar = inflatedView.findViewById<ProgressBar>(R.id.widget_prayer_progress)
        assertNotNull("ProgressBar must exist", progressBar)
        assertTrue(progressBar.progress in 0..100)

        val completedBadge = inflatedView.findViewById<TextView>(R.id.widget_completed_badge)
        assertEquals("تمت 2/5", completedBadge.text.toString())

        val timeViews = listOf(
            R.id.widget_fajr_time,
            R.id.widget_sunrise_time,
            R.id.widget_dhuhr_time,
            R.id.widget_asr_time,
            R.id.widget_maghrib_time,
            R.id.widget_isha_time
        )
        for (id in timeViews) {
            val tv = inflatedView.findViewById<TextView>(id)
            assertNotNull("Prayer time TextView $id missing", tv)
            assertTrue("Prayer time must not be blank", tv.text.toString().trim().isNotEmpty())
        }
    }

    @Test
    fun widgetHelpers_formatArabicPrefixesAndSplitTimesCleanly() {
        assertEquals("للفجر", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.FAJR))
        assertEquals("للشروق", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.SUNRISE))
        assertEquals("للظهر", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.DHUHR))
        assertEquals("للعصر", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.ASR))
        assertEquals("للمغرب", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.MAGHRIB))
        assertEquals("للعشاء", PrayerWidgetProvider.toPrefixedArabicPrayerName(PrayerType.ISHA))

        val (time, period) = PrayerWidgetProvider.splitTimeAndPeriod("03:25 م")
        assertEquals("03:25", time)
        assertEquals("م", period)
    }
}
