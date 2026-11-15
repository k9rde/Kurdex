package com.example.model

import android.content.Context

data class AdhanVoice(
    val id: String,
    val nameAr: String,
    val titleAr: String,
    val placeAr: String,
    val descriptionAr: String,
    val rawName: String
) {
    fun getAudioResId(context: Context): Int {
        return when (rawName) {
            "adhan_alafasy" -> com.example.R.raw.adhan_alafasy
            "adhan_abdulbasit" -> com.example.R.raw.adhan_abdulbasit
            "adhan_makkah" -> com.example.R.raw.adhan_makkah
            "adhan_madinah" -> com.example.R.raw.adhan_madinah
            "adhan_alaqsa" -> com.example.R.raw.adhan_alaqsa
            else -> {
                var resId = context.resources.getIdentifier(rawName, "raw", "com.example")
                if (resId == 0) {
                    resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
                }
                resId
            }
        }
    }

    companion object {
        val ALL_VOICES: List<AdhanVoice> = listOf(
            AdhanVoice(
                id = "alafasy",
                nameAr = "الشيخ مشاري راشد العفاسي",
                titleAr = "تكبيرات الأذان - العفاسي",
                placeAr = "الكويت",
                descriptionAr = "أداء خاشع وعذب ونبرة ندية بالتكبيرات",
                rawName = "adhan_alafasy"
            ),
            AdhanVoice(
                id = "abdulbasit",
                nameAr = "الشيخ عبد الباسط عبد الصمد",
                titleAr = "تكبيرات الأذان - عبد الباسط",
                placeAr = "جمهورية مصر العربية",
                descriptionAr = "صوت تاريخي خالد من أعظم قراء العالم الإسلامي",
                rawName = "adhan_abdulbasit"
            ),
            AdhanVoice(
                id = "makkah",
                nameAr = "الشيخ علي أحمد ملا",
                titleAr = "أذان الحرم المكي الشريف",
                placeAr = "مكة المكرمة",
                descriptionAr = "صوت بلال الحرم المكي الشريف المهيب",
                rawName = "adhan_makkah"
            ),
            AdhanVoice(
                id = "madinah",
                nameAr = "أذان المسجد النبوي الشريف",
                titleAr = "تكبيرات الحرم النبوي",
                placeAr = "المدينة المنورة",
                descriptionAr = "تكبيرات سكينة وطمأنينة من مدينة رسول الله ﷺ",
                rawName = "adhan_madinah"
            ),
            AdhanVoice(
                id = "alaqsa",
                nameAr = "أذان المسجد الأقصى المبارك",
                titleAr = "تكبيرات الأقصى الشريف",
                placeAr = "القدس الشريف",
                descriptionAr = "تكبيرات الأذان من رحاب المسجد الأقصى المبارك",
                rawName = "adhan_alaqsa"
            )
        )

        fun getById(id: String): AdhanVoice {
            return ALL_VOICES.find { it.id == id } ?: ALL_VOICES.first()
        }
    }
}
