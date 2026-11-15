package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.City
import com.example.model.TasbihItem
import com.example.util.CityData
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vakit_tesbih_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CITY_ID = "selected_city_id"
        private const val KEY_CUSTOM_LAT = "custom_lat"
        private const val KEY_CUSTOM_LNG = "custom_lng"
        private const val KEY_CUSTOM_CITY_NAME = "custom_city_name"
        private const val KEY_CUSTOM_COUNTRY_NAME = "custom_country_name"
        private const val KEY_CUSTOM_TIMEZONE = "custom_timezone"
        private const val KEY_IS_CUSTOM_LOCATION = "is_custom_location"
        private const val KEY_CALC_METHOD = "calc_method"
        private const val KEY_JURISTIC_METHOD = "juristic_method"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_FAVORITE_DUAS = "favorite_duas"
        private const val KEY_SAVED_TASBIHS = "saved_tasbihs"
        private const val KEY_SELECTED_TASBIH_ID = "selected_tasbih_id"
        private const val KEY_TOTAL_ALL_TIME_TASBIH = "total_all_time_tasbih"
        private const val KEY_AUTO_SYNC_ONLINE = "auto_sync_online"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_CACHED_ONLINE_TIMINGS = "cached_online_timings_"
        private const val KEY_WATER_OPACITY = "water_opacity"
        private const val KEY_ADHAN_NOTIFICATION_ENABLED = "adhan_notification_enabled"
        private const val KEY_ADHAN_VOICE_ID = "adhan_voice_id"
        private const val KEY_PRAYER_NOTIF_PREFIX = "prayer_notif_enabled_"
        private const val KEY_SELECTED_WALLPAPER = "selected_wallpaper_id"
        private const val KEY_DAILY_DUA_SHUFFLE_OFFSET = "daily_dua_shuffle_offset"
        private const val KEY_LAST_DAILY_DUA_DATE = "last_daily_dua_date"
        private const val KEY_ONLINE_DAILY_DUA_JSON = "online_daily_dua_json"
        private const val KEY_LAST_DISMISSED_UPDATE_VERSION = "last_dismissed_update_version"
        private const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val KEY_LOCATION_ONBOARDING_COMPLETED = "location_onboarding_completed"
        private const val KEY_PRE_PRAYER_REMINDER_ENABLED = "pre_prayer_reminder_enabled"
        private const val KEY_ONGOING_COUNTDOWN_ENABLED = "ongoing_countdown_enabled"
        private const val KEY_PRAYER_TRACKER_PREFIX = "prayer_tracker_"
        private const val KEY_LAST_READ_SURAH_ID = "last_read_surah_id"
        private const val KEY_QURAN_FONT_SIZE_SP = "quran_font_size_sp"
        private const val KEY_LAST_WIRD_COMPLETED_DATE = "last_wird_completed_date"
        private const val KEY_TOTAL_WIRDS_COUNT = "total_wirds_count"
        private const val KEY_CHALLENGE_TITLE = "shared_challenge_title"
        private const val KEY_CHALLENGE_TARGET = "shared_challenge_target"
        private const val KEY_CHALLENGE_MY_COUNT = "shared_challenge_my_count"
        private const val KEY_ADHKAR_PROGRESS_PREFIX = "adhkar_prog_"
        private const val KEY_CUSTOM_UPDATE_URL = "custom_update_url"
        private const val KEY_POST_PRAYER_FOLLOWUP_ENABLED = "post_prayer_followup_enabled"
    }

    var customUpdateUrl: String
        get() = prefs.getString(KEY_CUSTOM_UPDATE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_UPDATE_URL, value.trim()).apply()

    var isPrePrayerReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRE_PRAYER_REMINDER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PRE_PRAYER_REMINDER_ENABLED, value).apply()

    var isPostPrayerFollowupEnabled: Boolean
        get() = prefs.getBoolean(KEY_POST_PRAYER_FOLLOWUP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_POST_PRAYER_FOLLOWUP_ENABLED, value).apply()

    var isOngoingCountdownEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONGOING_COUNTDOWN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONGOING_COUNTDOWN_ENABLED, value).apply()

    var lastReadSurahId: String
        get() = prefs.getString(KEY_LAST_READ_SURAH_ID, "fatiha") ?: "fatiha"
        set(value) = prefs.edit().putString(KEY_LAST_READ_SURAH_ID, value).apply()

    var quranFontSizeSp: Float
        get() = prefs.getFloat(KEY_QURAN_FONT_SIZE_SP, 21f)
        set(value) = prefs.edit().putFloat(KEY_QURAN_FONT_SIZE_SP, value.coerceIn(16f, 32f)).apply()

    var lastWirdCompletedDate: String
        get() = prefs.getString(KEY_LAST_WIRD_COMPLETED_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_WIRD_COMPLETED_DATE, value).apply()

    var totalWirdsCount: Int
        get() = prefs.getInt(KEY_TOTAL_WIRDS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_WIRDS_COUNT, value).apply()

    var sharedChallengeTitle: String
        get() = prefs.getString(KEY_CHALLENGE_TITLE, "الصلاة على النبي ﷺ (تحدي اليوم)") ?: "الصلاة على النبي ﷺ (تحدي اليوم)"
        set(value) = prefs.edit().putString(KEY_CHALLENGE_TITLE, value).apply()

    var sharedChallengeTarget: Int
        get() = prefs.getInt(KEY_CHALLENGE_TARGET, 1000)
        set(value) = prefs.edit().putInt(KEY_CHALLENGE_TARGET, value.coerceAtLeast(10)).apply()

    var sharedChallengeMyCount: Int
        get() = prefs.getInt(KEY_CHALLENGE_MY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_CHALLENGE_MY_COUNT, value.coerceAtLeast(0)).apply()

    fun getCompletedPrayersForDate(dateKey: String): Set<String> {
        return prefs.getStringSet(KEY_PRAYER_TRACKER_PREFIX + dateKey, emptySet())?.toSet() ?: emptySet()
    }

    fun getTodayDateKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.MONTH) + 1}_${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    fun markPrayerCompleted(dateKey: String, prayerKey: String): Set<String> {
        val current = getCompletedPrayersForDate(dateKey).toMutableSet()
        current.add(prayerKey)
        prefs.edit().putStringSet(KEY_PRAYER_TRACKER_PREFIX + dateKey, current).apply()
        return current
    }

    fun togglePrayerCompleted(dateKey: String, prayerKey: String): Set<String> {
        val current = getCompletedPrayersForDate(dateKey).toMutableSet()
        if (current.contains(prayerKey)) {
            current.remove(prayerKey)
        } else {
            current.add(prayerKey)
        }
        prefs.edit().putStringSet(KEY_PRAYER_TRACKER_PREFIX + dateKey, current).apply()
        return current
    }

    fun calculatePrayerStreakDays(): Int {
        val cal = java.util.Calendar.getInstance()
        val formatKey: (java.util.Calendar) -> String = { c ->
            "${c.get(java.util.Calendar.YEAR)}_${c.get(java.util.Calendar.MONTH) + 1}_${c.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
        var streak = 0
        val todayCount = getCompletedPrayersForDate(formatKey(cal)).size
        if (todayCount >= 5) {
            streak = 1
        }
        // Count consecutive previous days with all 5 obligatory prayers logged
        for (i in 1..365) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (getCompletedPrayersForDate(formatKey(cal)).size >= 5) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    fun getAdhkarProgress(dateKey: String, itemId: String): Int {
        return prefs.getInt("${KEY_ADHKAR_PROGRESS_PREFIX}${dateKey}_$itemId", 0)
    }

    fun setAdhkarProgress(dateKey: String, itemId: String, count: Int) {
        prefs.edit().putInt("${KEY_ADHKAR_PROGRESS_PREFIX}${dateKey}_$itemId", count.coerceAtLeast(0)).apply()
    }

    var hasCompletedLocationOnboarding: Boolean
        get() = prefs.getBoolean(KEY_LOCATION_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION_ONBOARDING_COMPLETED, value).apply()

    var isDeveloperModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVELOPER_MODE_ENABLED, value).apply()

    var lastDismissedUpdateVersion: Int
        get() = prefs.getInt(KEY_LAST_DISMISSED_UPDATE_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_DISMISSED_UPDATE_VERSION, value).apply()

    var dailyDuaShuffleOffset: Int
        get() = prefs.getInt(KEY_DAILY_DUA_SHUFFLE_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_DUA_SHUFFLE_OFFSET, value).apply()

    var lastDailyDuaDate: String
        get() = prefs.getString(KEY_LAST_DAILY_DUA_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_DAILY_DUA_DATE, value).apply()

    var onlineDailyDuaJson: String?
        get() = prefs.getString(KEY_ONLINE_DAILY_DUA_JSON, null)
        set(value) = prefs.edit().putString(KEY_ONLINE_DAILY_DUA_JSON, value).apply()

    var selectedWallpaperId: String
        get() {
            val id = prefs.getString(KEY_SELECTED_WALLPAPER, "kaaba_night") ?: "kaaba_night"
            return if (id == "prayer_man") "kaaba_night" else id
        }
        set(value) = prefs.edit().putString(KEY_SELECTED_WALLPAPER, value).apply()

    var isAdhanNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADHAN_NOTIFICATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADHAN_NOTIFICATION_ENABLED, value).apply()

    var selectedAdhanVoiceId: String
        get() = prefs.getString(KEY_ADHAN_VOICE_ID, "alafasy") ?: "alafasy"
        set(value) = prefs.edit().putString(KEY_ADHAN_VOICE_ID, value).apply()

    fun isPrayerNotificationEnabled(prayerName: String): Boolean {
        return prefs.getBoolean(KEY_PRAYER_NOTIF_PREFIX + prayerName, true)
    }

    fun setPrayerNotificationEnabled(prayerName: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRAYER_NOTIF_PREFIX + prayerName, enabled).apply()
    }

    /**
     * Controls the frosted glass design blur/opacity (طمس وضبابية التصميم المائي) from 0.0f (0%) to 1.0f (100%).
     */
    var waterOpacity: Float
        get() = prefs.getFloat(KEY_WATER_OPACITY, 0.65f)
        set(value) = prefs.edit().putFloat(KEY_WATER_OPACITY, value).apply()

    var isAutoSyncOnlineEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_ONLINE, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    fun getCachedDayTimings(key: String): String? = prefs.getString(KEY_CACHED_ONLINE_TIMINGS + key, null)

    fun saveCachedDayTimings(key: String, json: String) {
        prefs.edit().putString(KEY_CACHED_ONLINE_TIMINGS + key, json).apply()
    }

    fun getSelectedCity(): City {
        val isCustom = prefs.getBoolean(KEY_IS_CUSTOM_LOCATION, false)
        if (isCustom) {
            val name = prefs.getString(KEY_CUSTOM_CITY_NAME, "موقعي") ?: "موقعي"
            val country = prefs.getString(KEY_CUSTOM_COUNTRY_NAME, "موقعي") ?: "موقعي"
            val lat = prefs.getString("${KEY_CUSTOM_LAT}_str", null)?.toDoubleOrNull()
                ?: prefs.getFloat(KEY_CUSTOM_LAT, 36.3489f).toDouble()
            val lng = prefs.getString("${KEY_CUSTOM_LNG}_str", null)?.toDoubleOrNull()
                ?: prefs.getFloat(KEY_CUSTOM_LNG, 43.1577f).toDouble()
            val tz = prefs.getFloat(KEY_CUSTOM_TIMEZONE, 3.0f).toDouble()
            return City(
                id = "custom_gps",
                name = name,
                nameAr = name,
                country = country,
                countryAr = country,
                latitude = lat,
                longitude = lng,
                timezone = tz
            )
        }
        val defaultId = "iq_05" // Mosul, Iraq
        val cityId = prefs.getString(KEY_CITY_ID, defaultId)
        val resolvedId = if (cityId == null || cityId == "sa_01") defaultId else cityId
        return CityData.allCities.find { it.id == resolvedId } ?: CityData.defaultCity
    }

    fun setSelectedCity(city: City) {
        if (city.id == "custom" || city.id == "custom_gps") {
            prefs.edit()
                .putBoolean(KEY_IS_CUSTOM_LOCATION, true)
                .putFloat(KEY_CUSTOM_LAT, city.latitude.toFloat())
                .putFloat(KEY_CUSTOM_LNG, city.longitude.toFloat())
                .putString("${KEY_CUSTOM_LAT}_str", city.latitude.toString())
                .putString("${KEY_CUSTOM_LNG}_str", city.longitude.toString())
                .putString(KEY_CUSTOM_CITY_NAME, city.nameAr.ifEmpty { city.name })
                .putString(KEY_CUSTOM_COUNTRY_NAME, city.countryAr.ifEmpty { city.country })
                .putFloat(KEY_CUSTOM_TIMEZONE, city.timezone.toFloat())
                .apply()
        } else {
            prefs.edit()
                .putBoolean(KEY_IS_CUSTOM_LOCATION, false)
                .putString(KEY_CITY_ID, city.id)
                .apply()
        }
    }

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var calculationMethodIndex: Int
        get() {
            val v = prefs.getInt(KEY_CALC_METHOD, -1)
            // If unset or left on old default (0 - Makkah), default to 2 (Muslim World League - standard for Iraq/Mosul)
            return if (v <= 0) 2 else v
        }
        set(value) = prefs.edit().putInt(KEY_CALC_METHOD, value).apply()

    fun getPrayerOffset(prayerName: String): Int {
        return prefs.getInt("prayer_offset_" + prayerName.lowercase(), 0)
    }

    fun setPrayerOffset(prayerName: String, minutes: Int) {
        prefs.edit().putInt("prayer_offset_" + prayerName.lowercase(), minutes).apply()
    }

    var juristicMethodIndex: Int
        get() = prefs.getInt(KEY_JURISTIC_METHOD, 0)
        set(value) = prefs.edit().putInt(KEY_JURISTIC_METHOD, value).apply()

    fun getFavoriteDuaIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_DUAS, setOf("dua_gunun", "dua_ayetel_kursi", "dua_sayyidul_istigfar")) ?: emptySet()
    }

    fun toggleFavoriteDua(id: String) {
        val current = getFavoriteDuaIds().toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_DUAS, current).apply()
    }

    fun getTotalAllTimeTasbih(): Int = prefs.getInt(KEY_TOTAL_ALL_TIME_TASBIH, 0)

    fun incrementTotalAllTimeTasbih() {
        val count = getTotalAllTimeTasbih() + 1
        prefs.edit().putInt(KEY_TOTAL_ALL_TIME_TASBIH, count).apply()
    }

    fun getSelectedTasbihId(): String = prefs.getString(KEY_SELECTED_TASBIH_ID, "dhikr_subhanallah") ?: "dhikr_subhanallah"

    fun setSelectedTasbihId(id: String) {
        prefs.edit().putString(KEY_SELECTED_TASBIH_ID, id).apply()
    }

    private fun mapCategoryToAr(cat: String): String = when (cat) {
        "Namaz Tesbihatı" -> "أذكار الصلاة"
        "Tevhid" -> "التوحيد"
        "İstiğfar" -> "الاستغفار"
        "Salavat" -> "الصلاة على النبي"
        "Tevekkül" -> "التوكل والحوقلة"
        "Faziletli Zikirler" -> "فضائل الأذكار"
        "Esmâ-ül Hüsnâ" -> "أسماء الله الحسنى"
        "Genel" -> "أذكار عامة"
        else -> cat
    }

    fun loadTasbihs(): List<TasbihItem> {
        val jsonStr = prefs.getString(KEY_SAVED_TASBIHS, null)
        if (jsonStr.isNullOrEmpty()) {
            return defaultTasbihs()
        }
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TasbihItem>()
            val defaultsMap = defaultTasbihs().associateBy { it.id }

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val def = defaultsMap[id]

                val titleTr = if (def != null) def.titleTr else obj.getString("titleTr")
                val titleAr = if (def != null) def.titleAr else obj.optString("titleAr", "")
                val meaningTr = if (def != null) def.meaningTr else obj.optString("meaningTr", "")
                val category = if (def != null) def.category else mapCategoryToAr(obj.optString("category", "أذكار عامة"))

                list.add(
                    TasbihItem(
                        id = id,
                        titleTr = titleTr,
                        titleAr = titleAr,
                        meaningTr = meaningTr,
                        target = obj.optInt("target", 33),
                        currentCount = obj.optInt("currentCount", 0),
                        totalCount = obj.optInt("totalCount", 0),
                        lapCount = obj.optInt("lapCount", 0),
                        isCustom = obj.optBoolean("isCustom", false),
                        category = category
                    )
                )
            }
            if (list.isEmpty()) defaultTasbihs() else list
        } catch (e: Exception) {
            defaultTasbihs()
        }
    }

    fun saveTasbihs(items: List<TasbihItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("titleTr", item.titleTr)
                put("titleAr", item.titleAr)
                put("meaningTr", item.meaningTr)
                put("target", item.target)
                put("currentCount", item.currentCount)
                put("totalCount", item.totalCount)
                put("lapCount", item.lapCount)
                put("isCustom", item.isCustom)
                put("category", item.category)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_TASBIHS, array.toString()).apply()
    }

    private fun defaultTasbihs(): List<TasbihItem> = listOf(
        TasbihItem(
            id = "dhikr_subhanallah",
            titleTr = "سبحان الله",
            titleAr = "سُبْحَانَ اللَّهِ",
            meaningTr = "تنزيه الله تعالى وبراءته من كل نقص وعيب",
            target = 33,
            category = "أذكار الصلاة"
        ),
        TasbihItem(
            id = "dhikr_elhamdulillah",
            titleTr = "الحمد لله",
            titleAr = "الْحَمْدُ لِلَّهِ",
            meaningTr = "الثناء والشكر والحمد لله وحده على جميع نعمه",
            target = 33,
            category = "أذكار الصلاة"
        ),
        TasbihItem(
            id = "dhikr_allahuekber",
            titleTr = "الله أكبر",
            titleAr = "اللَّهُ أَكْبَرُ",
            meaningTr = "الله أعظم وأجلّ وأكبر من كل شيء في الوجود",
            target = 33,
            category = "أذكار الصلاة"
        ),
        TasbihItem(
            id = "dhikr_lailaheillallah",
            titleTr = "لا إله إلا الله",
            titleAr = "لَا إِلٰهَ إِلَّا اللَّهُ",
            meaningTr = "لا معبود بحق في الوجود إلا الله وحده لا شريك له",
            target = 100,
            category = "التوحيد"
        ),
        TasbihItem(
            id = "dhikr_estagfirullah",
            titleTr = "أستغفر الله",
            titleAr = "أَسْتَغْفِرُ اللَّهَ",
            meaningTr = "طلب المغفرة والستر والعفو من الله الغفور الرحيم",
            target = 100,
            category = "الاستغفار"
        ),
        TasbihItem(
            id = "dhikr_salavat",
            titleTr = "اللهم صل على محمد",
            titleAr = "اللَّهُمَّ صَلِّ عَلَى سَيِّدِنَا مُحَمَّدٍ",
            meaningTr = "طلب الرحمة والبركة ورفعة الدرجات للنبي المصطفى ﷺ",
            target = 100,
            category = "الصلاة على النبي"
        ),
        TasbihItem(
            id = "dhikr_lahavle",
            titleTr = "لا حول ولا قوة إلا بالله",
            titleAr = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
            meaningTr = "كنز من كنوز الجنة، لا تحول عن معصية ولا ثبات على طاعة إلا بالله",
            target = 33,
            category = "التوكل والحوقلة"
        ),
        TasbihItem(
            id = "dhikr_subhanallahi_vebihamdihi",
            titleTr = "سبحان الله وبحمده",
            titleAr = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            meaningTr = "تسبيح يغفر الذنوب وإن كانت مثل زبد البحر",
            target = 100,
            category = "فضائل الأذكار"
        ),
        TasbihItem(
            id = "dhikr_ya_shafi",
            titleTr = "يا شافي",
            titleAr = "يَا شَافِي",
            meaningTr = "دعاء بالشفاء من كل داء وسقم باسم الله الشافي",
            target = 100,
            category = "أسماء الله الحسنى"
        ),
        TasbihItem(
            id = "dhikr_ya_rezzak",
            titleTr = "يا رزاق",
            titleAr = "يَا رَزَّاقُ",
            meaningTr = "سؤال الله سعة الرزق والبركة باسمه الرزاق ذي القوة المتين",
            target = 100,
            category = "أسماء الله الحسنى"
        ),
        TasbihItem(
            id = "dhikr_ya_fettah",
            titleTr = "يا فتاح",
            titleAr = "يَا فَتَّاحُ",
            meaningTr = "سؤال الفتح والتيسير والنجاح في كل خير باسم الله الفتاح",
            target = 100,
            category = "أسماء الله الحسنى"
        )
    )
}
