package com.example

import com.example.model.CalculationMethod
import com.example.model.JuristicMethod
import com.example.util.CityData
import com.example.util.PrayerCalculator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPrayerCalculationForMakkahAndCairo() {
        // Makkah
        val timesMakkah = PrayerCalculator.calculateTimes(
            year = 2026,
            month = 11,
            day = 1,
            lat = 21.4225,
            lng = 39.8262,
            timeZone = 3.0,
            method = CalculationMethod.MAKKAH,
            juristic = JuristicMethod.STANDARD
        )

        assertNotNull(timesMakkah)
        val regex12 = Regex("\\d{2}:\\d{2} [صم]")
        assertTrue("Fajr should match 12h format: ${timesMakkah.fajr}", timesMakkah.fajr.matches(regex12))
        assertTrue("Sunrise should match 12h format: ${timesMakkah.sunrise}", timesMakkah.sunrise.matches(regex12))
        assertTrue("Dhuhr should match 12h format: ${timesMakkah.dhuhr}", timesMakkah.dhuhr.matches(regex12))
        assertTrue("Asr should match 12h format: ${timesMakkah.asr}", timesMakkah.asr.matches(regex12))
        assertTrue("Maghrib should match 12h format: ${timesMakkah.maghrib}", timesMakkah.maghrib.matches(regex12))
        assertTrue("Isha should match 12h format: ${timesMakkah.isha}", timesMakkah.isha.matches(regex12))
        assertEquals(6, timesMakkah.items.size)

        // Cairo
        val timesCairo = PrayerCalculator.calculateTimes(
            year = 2026,
            month = 11,
            day = 1,
            lat = 30.0444,
            lng = 31.2357,
            timeZone = 2.0,
            method = CalculationMethod.EGYPT,
            juristic = JuristicMethod.STANDARD
        )
        assertNotNull(timesCairo)
        assertTrue(timesCairo.fajr.matches(regex12))
    }

    @Test
    fun test12HourFormatWithArabicAmPm() {
        assertEquals("12:25 ص", PrayerCalculator.format12FromHourMinute(0, 25))
        assertEquals("01:25 ص", PrayerCalculator.format12FromHourMinute(1, 25))
        assertEquals("05:10 ص", PrayerCalculator.format12FromHourMinute(5, 10))
        assertEquals("12:00 م", PrayerCalculator.format12FromHourMinute(12, 0))
        assertEquals("03:45 م", PrayerCalculator.format12FromHourMinute(15, 45))
        assertEquals("07:30 م", PrayerCalculator.format12FromHourMinute(19, 30))
    }

    @Test
    fun testMosulCityAndPrayerTimes() {
        val mosulList = CityData.findCity("الموصل")
        assertTrue("Mosul should exist in CityData", mosulList.isNotEmpty())
        val mosul = mosulList.first()
        assertEquals("الموصل", mosul.nameAr)
        assertEquals("العراق", mosul.countryAr)
        assertEquals(3.0, mosul.timezone, 0.001)

        val mosulTimes = PrayerCalculator.calculateTimes(
            year = 2026,
            month = 11,
            day = 1,
            lat = mosul.latitude,
            lng = mosul.longitude,
            timeZone = mosul.timezone,
            method = CalculationMethod.MAKKAH,
            juristic = JuristicMethod.STANDARD
        )

        assertNotNull(mosulTimes)
        val regex12 = Regex("\\d{2}:\\d{2} [صم]")
        assertTrue("Fajr: ${mosulTimes.fajr}", mosulTimes.fajr.matches(regex12))
        assertTrue("Dhuhr: ${mosulTimes.dhuhr}", mosulTimes.dhuhr.matches(regex12))
        assertTrue("Asr: ${mosulTimes.asr}", mosulTimes.asr.matches(regex12))
        assertTrue("Maghrib: ${mosulTimes.maghrib}", mosulTimes.maghrib.matches(regex12))
        assertTrue("Isha: ${mosulTimes.isha}", mosulTimes.isha.matches(regex12))
    }

    @Test
    fun testQiblaAngleCalculation() {
        val qiblaCairo = PrayerCalculator.getQiblaAngle(30.0444, 31.2357)
        // Cairo to Mecca is roughly 136 degrees south-southeast
        assertTrue(qiblaCairo in 125.0..145.0)

        val distance = PrayerCalculator.getKaabaDistanceKm(30.0444, 31.2357)
        assertTrue(distance in 1200..1400)
    }

    @Test
    fun testGlobalCountriesAndCitiesLoaded() {
        assertTrue(CityData.allCities.size >= 80)
        val makkah = CityData.findCity("مكة")
        assertTrue(makkah.isNotEmpty())
        assertEquals("مكة المكرمة", makkah.first().nameAr)

        val cairo = CityData.findCity("Cairo")
        assertTrue(cairo.isNotEmpty())
        assertEquals("مصر", cairo.first().countryAr)

        val algeria = CityData.findCity("الجزائر")
        assertTrue(algeria.isNotEmpty())
    }

    @Test
    fun testAdhanVoicesListAndLookup() {
        val voices = com.example.model.AdhanVoice.ALL_VOICES
        assertTrue("At least 5 imams available", voices.size >= 5)

        val alafasy = com.example.model.AdhanVoice.getById("alafasy")
        assertEquals("الشيخ مشاري راشد العفاسي", alafasy.nameAr)
        assertEquals("adhan_alafasy", alafasy.rawName)

        val abdulbasit = com.example.model.AdhanVoice.getById("abdulbasit")
        assertEquals("الشيخ عبد الباسط عبد الصمد", abdulbasit.nameAr)
        assertEquals("adhan_abdulbasit", abdulbasit.rawName)

        val makkah = com.example.model.AdhanVoice.getById("makkah")
        assertEquals("الشيخ علي أحمد ملا", makkah.nameAr)
        assertEquals("adhan_makkah", makkah.rawName)

        val madinah = com.example.model.AdhanVoice.getById("madinah")
        assertTrue(madinah.nameAr.contains("النبوي"))

        val alaqsa = com.example.model.AdhanVoice.getById("alaqsa")
        assertTrue(alaqsa.nameAr.contains("الأقصى"))
    }

    @Test
    fun testDailyDuaRotationAndOfflineLibrary() {
        val dailyDuas = com.example.model.DuaData.dailyDuasLibrary
        assertTrue("Daily library should have at least 31 rotating duas", dailyDuas.size >= 31)

        for (dua in dailyDuas) {
            assertTrue("Dua arabic text must not be empty", dua.arabicText.isNotBlank())
            assertTrue("Dua title must not be empty", dua.titleAr.isNotBlank())
            assertTrue("Dua source must not be empty", dua.source.isNotBlank())
            assertTrue("Dua meaning must not be empty", dua.meaningAr.isNotBlank())
        }

        val calDay1 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 1)
        }
        val calDay2 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 2)
        }

        val duaDay1 = com.example.model.DuaData.getDailyDuaForDate(calDay1, shuffleOffset = 0)
        val duaDay2 = com.example.model.DuaData.getDailyDuaForDate(calDay2, shuffleOffset = 0)

        assertNotNull("Dua for Day 1 should not be null", duaDay1)
        assertNotNull("Dua for Day 2 should not be null", duaDay2)
        assertNotEquals("Day 1 and Day 2 should have different rotating duas", duaDay1.id, duaDay2.id)

        // Test manual shuffle / cycle
        val shuffledDua = com.example.model.DuaData.getDailyDuaForDate(calDay1, shuffleOffset = 1)
        assertNotNull(shuffledDua)
        assertNotEquals("Offset 1 should yield different dua than offset 0", duaDay1.id, shuffledDua.id)
    }

    @Test
    fun testAppUpdateInfoAndLogic() {
        val updateInfo = com.example.model.AppUpdateInfo(
            isUpdateAvailable = true,
            currentVersionCode = 1,
            currentVersionName = "1.2.0",
            latestVersionCode = 2,
            latestVersionName = "1.3.0",
            releaseNotesAr = "ميزات إضافية جديدة",
            telegramChannelUrl = com.example.util.AppUpdateChecker.TELEGRAM_CHANNEL_URL,
            directApkDownloadUrl = com.example.util.AppUpdateChecker.DIRECT_APK_DOWNLOAD_URL,
            isMandatory = false
        )

        assertTrue(updateInfo.isUpdateAvailable)
        assertEquals(1, updateInfo.currentVersionCode)
        assertEquals(2, updateInfo.latestVersionCode)
        assertTrue(updateInfo.latestVersionCode > updateInfo.currentVersionCode)
        assertEquals("https://t.me/AS3SA", updateInfo.telegramChannelUrl)
        assertTrue(updateInfo.directApkDownloadUrl.endsWith("salati-app.apk"))

        val downloadingInfo = updateInfo.copy(
            isDownloading = true,
            downloadProgress = 0.5f,
            downloadPercentage = 50,
            downloadProgressText = "14.5 ميغابايت / 29.0 ميغابايت"
        )
        assertTrue(downloadingInfo.isDownloading)
        assertEquals(0.5f, downloadingInfo.downloadProgress, 0.001f)
        assertEquals(50, downloadingInfo.downloadPercentage)
        assertEquals("14.5 ميغابايت / 29.0 ميغابايت", downloadingInfo.downloadProgressText)
    }

    @Test
    fun testQuranWirdAndInteractiveAdhkarData() {
        val surahs = com.example.model.QuranWirdData.surahs
        assertTrue("Should have curated Quran Wird surahs", surahs.size >= 5)
        assertTrue("Kahf should be Friday recommended", surahs.any { it.id == "kahf_wird" && it.isFridayRecommended })

        val periods = com.example.model.InteractiveAdhkarData.periods
        assertEquals(4, periods.size)
        for (period in periods) {
            val items = com.example.model.InteractiveAdhkarData.allItems.filter { it.period == period }
            assertTrue("Period $period should have items", items.isNotEmpty())
            assertTrue("All items in $period must have positive target", items.all { it.targetCount > 0 && it.arabicText.isNotBlank() })
        }
    }

    @Test
    fun testQiblaAtMakkahAndVersionConstants() {
        // Check Mosul Iraq Qibla angle roughly south (~190 degrees)
        val qiblaMosul = PrayerCalculator.getQiblaAngle(36.3489, 43.1577)
        assertTrue("Mosul Qibla should point South/Southwest (~190 deg), got $qiblaMosul", qiblaMosul in 180.0..200.0)
        assertEquals(13, com.example.util.AppUpdateChecker.CURRENT_VERSION_CODE)
        assertEquals("1.4.9", com.example.util.AppUpdateChecker.CURRENT_VERSION_NAME)
    }

    @Test
    fun testGitHubUpdateUrlResolutionAndVersionComparison() {
        val urls = com.example.util.AppUpdateChecker.resolveCandidateUpdateUrls("https://github.com/myuser/salati-app")
        assertTrue(urls.any { it == "https://raw.githubusercontent.com/myuser/salati-app/main/download-web/version.json" })
        assertTrue(urls.any { it == "https://raw.githubusercontent.com/myuser/salati-app/master/download-web/version.json" })
        assertTrue(urls.any { it == "https://api.github.com/repos/myuser/salati-app/releases/latest" })

        assertTrue(com.example.util.AppUpdateChecker.isRemoteVersionNewer(13, 13, "1.5.0", "1.4.9"))
        assertFalse(com.example.util.AppUpdateChecker.isRemoteVersionNewer(13, 13, "1.4.9", "1.4.9"))
        assertTrue(com.example.util.AppUpdateChecker.isRemoteVersionNewer(14, 13, "1.4.9", "1.4.9"))
    }

    @Test
    fun testReleaseNotesEmojiFormattingAndExtraction() {
        val raw = "• إضافة بوصلة القبلة الذكية\n- تحديث الأدعية والأذكار اليومية\n• مزامنة دورية عبر WorkManager عند تغير الموقع"
        val items = com.example.util.AppUpdateChecker.extractReleaseNoteItems(raw)
        assertEquals(3, items.size)
        assertEquals("🧭", items[0].first)
        assertEquals("🤲", items[1].first)
        assertEquals("🔄", items[2].first)

        val alreadyPrefixed = "🔔 إشعار الأذان التلقائي\n📱 تصميم زجاجي محسن"
        val parsed = com.example.util.AppUpdateChecker.extractReleaseNoteItems(alreadyPrefixed)
        assertEquals("🔔", parsed[0].first)
        assertEquals("إشعار الأذان التلقائي", parsed[0].second)
        assertEquals("📱", parsed[1].first)
        assertEquals("تصميم زجاجي محسن", parsed[1].second)
    }
}
