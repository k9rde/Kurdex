package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

data class AppWallpaper(
    val id: String,
    val nameAr: String,
    val descriptionAr: String,
    @DrawableRes val drawableRes: Int?,
    val isSolidColor: Boolean = false
) {
    companion object {
        val ALL_WALLPAPERS: List<AppWallpaper> = listOf(
            AppWallpaper(
                id = "kaaba_night",
                nameAr = "الكعبة المشرفة ليلاً",
                descriptionAr = "مشهد مهيب للكعبة المشرفة مع سماء ليلية وهلال منير",
                drawableRes = R.drawable.img_bg_kaaba_night_1793806365017
            ),
            AppWallpaper(
                id = "islamic_night",
                nameAr = "المسجد والنجوم الإسلامية",
                descriptionAr = "مسجد ملكي تحت سماء مرصعة بالنجوم وهلال وزخارف إسلامية",
                drawableRes = R.drawable.img_bg_islamic_night_1793806380751
            ),
            AppWallpaper(
                id = "emerald_water",
                nameAr = "التموج المائي الزمردي",
                descriptionAr = "خلفية زجاجية سائلة باللون الأخضر الملكي وتأثير مائي فاخر",
                drawableRes = R.drawable.glass_water_bg_1793570031823
            ),
            AppWallpaper(
                id = "golden_art",
                nameAr = "الزخرفة الذهبية الملكية",
                descriptionAr = "فنون وزخارف إسلامية أصيلة بلمسات ذهبية دافئة",
                drawableRes = R.drawable.hero_islamic_art_1793316821083
            ),
            AppWallpaper(
                id = "midnight_dark",
                nameAr = "الأسود الملكي الفاخر",
                descriptionAr = "خلفية داكنة موفرة للطاقة تركز على راحة العين وجمال النصوص",
                drawableRes = null,
                isSolidColor = true
            )
        )

        fun getById(id: String): AppWallpaper {
            return ALL_WALLPAPERS.find { it.id == id } ?: ALL_WALLPAPERS.first()
        }
    }
}
