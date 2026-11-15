package com.example.model

enum class PrayerType(
    val titleTr: String,
    val titleAr: String,
    val iconName: String
) {
    FAJR("الفجر", "الفجر", "fajr"),
    SUNRISE("الشروق", "الشروق", "sunrise"),
    DHUHR("الظهر", "الظهر", "dhuhr"),
    ASR("العصر", "العصر", "asr"),
    MAGHRIB("المغرب", "المغرب", "maghrib"),
    ISHA("العشاء", "العشاء", "isha")
}

data class PrayerItem(
    val type: PrayerType,
    val timeFormatted: String,
    val hour: Int,
    val minute: Int,
    val timestampMillis: Long,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false
)

data class DayPrayerTimes(
    val dateFormatted: String,
    val hijriFormatted: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val midnight: String,
    val items: List<PrayerItem> = emptyList(),
    val nextPrayer: PrayerItem? = null,
    val previousPrayer: PrayerItem? = null,
    val countdownFormatted: String = "",
    val progress: Float = 0f,
    val isKerahat: Boolean = false,
    val kerahatMessage: String? = null,
    val isOnlineVerified: Boolean = false
)

data class City(
    val id: String,
    val name: String,
    val nameAr: String = "",
    val country: String = "العالم",
    val countryAr: String = "",
    val latitude: Double,
    val longitude: Double,
    val timezone: Double = 3.0,
    val region: String = "العالم"
)

enum class CalculationMethod(val title: String, val titleAr: String, val fajrAngle: Double, val ishaAngle: Double, val aladhanId: Int) {
    MAKKAH("أم القرى (مكة المكرمة)", "أم القرى (مكة المكرمة)", 18.5, 19.0, 4),
    EGYPT("الهيئة المصرية العامة للمساحة", "الهيئة المصرية العامة للمساحة", 19.5, 17.5, 5),
    MWL("رابطة العالم الإسلامي", "رابطة العالم الإسلامي", 18.0, 17.0, 3),
    DIYANET("رئاسة الشؤون الدينية التركية", "رئاسة الشؤون الدينية التركية", 18.0, 17.0, 13),
    ISNA("الجمعية الإسلامية لأمريكا الشمالية", "الجمعية الإسلامية لأمريكا الشمالية", 15.0, 15.0, 2),
    KUWAIT("وزارة الأوقاف والشؤون الإسلامية بالكويت", "وزارة الأوقاف والشؤون الإسلامية بالكويت", 18.0, 17.5, 9),
    QATAR("وزارة الأوقاف والشؤون الإسلامية بقطر", "وزارة الأوقاف والشؤون الإسلامية بقطر", 18.0, 18.0, 10),
    DUBAI("دبي / الإمارات العربية المتحدة", "دبي / الإمارات العربية المتحدة", 18.2, 18.2, 16),
    TEHRAN("معهد الجيوفيزياء بجامعة طهران", "معهد الجيوفيزياء بجامعة طهران", 17.7, 14.0, 7)
}

enum class JuristicMethod(val title: String, val titleAr: String, val shadowFactor: Double) {
    STANDARD("الجمهور (شافعي / مالكي / حنبلي)", "الجمهور (شافعي / مالكي / حنبلي)", 1.0),
    HANAFI("المذهب الحنفي", "المذهب الحنفي", 2.0)
}
