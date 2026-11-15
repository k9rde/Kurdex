package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import java.io.File
import java.io.FileOutputStream

/**
 * Generates crisp, high-resolution Islamic Liquid Glass Story cards
 * at exact Instagram Stories 9:16 dimensions (1080 x 1920)
 * featuring the app icon in the corner and pure PNG image sharing.
 */
object ShareCardGenerator {

    const val STORY_WIDTH = 1080
    const val STORY_HEIGHT = 1920

    fun shareStoryCard(
        context: Context,
        badgeLabel: String,
        title: String,
        arabicBody: String,
        source: String,
        cityAndDate: String
    ) {
        try {
            val bitmap = renderCardBitmap(
                context = context,
                badgeLabel = badgeLabel,
                title = title,
                arabicBody = arabicBody,
                source = source,
                cityAndDate = cityAndDate
            )
            val cacheDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            // Clean previous temporary story images to avoid cache bloat
            cacheDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile) oldFile.delete()
            }

            val outFile = File(cacheDir, "salati_story_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outFile
            )

            val shareIntent = createPureImageShareIntent(uri)
            val chooser = Intent.createChooser(shareIntent, "مشاركة الصورة للستوري").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$title\n\n$arabicBody\n\n— $source\n(تطبيق صلاتي وأذكاري)")
            }
            val chooser = Intent.createChooser(fallbackIntent, "مشاركة").apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        }
    }

    internal fun createPureImageShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("صورة للستوري", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun renderCardBitmap(
        context: Context,
        badgeLabel: String,
        title: String,
        arabicBody: String,
        source: String,
        cityAndDate: String
    ): Bitmap {
        val width = STORY_WIDTH
        val height = STORY_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val arabicBodyTypeface = loadTypefaceSafe(context, R.font.amiri, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val arabicUiTypeface = loadTypefaceSafe(context, R.font.cairo, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))

        // 1. Deep Emerald Night Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF03110C.toInt(), 0xFF0A2C20.toInt(), 0xFF04150E.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Subtle Islamic Geometric Diamond Lattice Watermark
        val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x12D4AF37
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val step = 180f
        var py = 0f
        while (py <= height + step) {
            var px = 0f
            while (px <= width + step) {
                val half = step * 0.5f
                val diamond = Path().apply {
                    moveTo(px, py - half)
                    lineTo(px + half, py)
                    lineTo(px, py + half)
                    lineTo(px - half, py)
                    close()
                }
                canvas.drawPath(diamond, patternPaint)
                px += step
            }
            py += step
        }

        // 3. Luminous Radial Golden-Emerald Aura behind Center Card
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.48f, 640f,
                intArrayOf(0x3CD4AF37, 0x20135D48, 0x00000000),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.5f, height * 0.48f, 640f, glowPaint)

        // 4. Double Ornamental Gold Outer Frame
        val outerFrameRect = RectF(42f, 42f, width - 42f, height - 42f)
        val outerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            shader = LinearGradient(
                outerFrameRect.left, outerFrameRect.top, outerFrameRect.right, outerFrameRect.bottom,
                intArrayOf(0xCCF3E5AB.toInt(), 0x5510B981, 0xCCD4AF37.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(outerFrameRect, 52f, 52f, outerFramePaint)

        val innerFrameRect = RectF(56f, 56f, width - 56f, height - 56f)
        val innerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            color = 0x44F3E5AB
        }
        canvas.drawRoundRect(innerFrameRect, 42f, 42f, innerFramePaint)

        // 5. App Icon + Brand Lockup in the Top-Right Corner (IG Story safe zone)
        val iconSize = 112f
        val iconRight = width - 82f
        val iconLeft = iconRight - iconSize
        val iconTop = 104f
        val iconBottom = iconTop + iconSize
        val iconRect = RectF(iconLeft, iconTop, iconRight, iconBottom)

        // Subtle golden glow behind corner icon
        val iconGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                iconRect.centerX(), iconRect.centerY(), iconSize * 0.9f,
                intArrayOf(0x4CD4AF37, 0x00000000),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.9f, iconGlowPaint)

        val iconBitmap = loadAppIconBitmap(context)
        if (iconBitmap != null) {
            val iconClipPath = Path().apply {
                addRoundRect(iconRect, 28f, 28f, Path.Direction.CW)
            }
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            canvas.clipPath(iconClipPath)
            canvas.drawBitmap(iconBitmap, null, iconRect, bitmapPaint)
            canvas.restore()
            if (!iconBitmap.isRecycled) {
                iconBitmap.recycle()
            }
        } else {
            val fallbackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A231A.toInt() }
            canvas.drawRoundRect(iconRect, 28f, 28f, fallbackBgPaint)
        }

        val iconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFE8C96D.toInt()
        }
        canvas.drawRoundRect(iconRect, 28f, 28f, iconBorderPaint)

        val brandNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3E5AB.toInt()
            textSize = 32f
            typeface = arabicUiTypeface
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("صلاتي وأذكاري", iconLeft - 22f, iconTop + 48f, brandNamePaint)

        val brandSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9DE2C0.toInt()
            textSize = 22f
            typeface = arabicUiTypeface
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("رفيقك اليومي للعبادة", iconLeft - 22f, iconTop + 86f, brandSubPaint)

        // 6. Central High-Contrast Liquid Glass Card (936 x 1456)
        val cardRect = RectF(72f, 260f, width - 72f, height - 204f)

        // Deep emerald frosted backing ensures >12:1 legibility for crisp white Arabic script
        val cardBackingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8C041610.toInt()
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBackingPaint)

        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(0x28FFFFFF, 0x0EFFFFFF, 0x220D402E),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardFillPaint)

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(0x99F3E5AB.toInt(), 0x44FFFFFF, 0x88D4AF37.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBorderPaint)

        // 7. Top Category Badge Pill
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF4C8.toInt()
            textSize = 31f
            typeface = arabicUiTypeface
            textAlign = Paint.Align.CENTER
        }
        val cleanBadge = badgeLabel.ifBlank { "بطاقة إيمانية" }
        val badgeDisplay = "✦  $cleanBadge  ✦"
        val badgeWidth = (badgeTextPaint.measureText(badgeDisplay) + 84f).coerceAtMost(cardRect.width() - 80f)
        val badgeLeft = (width - badgeWidth) / 2f
        val badgeRect = RectF(badgeLeft, cardRect.top + 48f, badgeLeft + badgeWidth, cardRect.top + 118f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4AD4AF37 }
        val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = 0x77F3E5AB
        }
        canvas.drawRoundRect(badgeRect, 35f, 35f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 35f, 35f, badgeStrokePaint)
        canvas.drawText(badgeDisplay, width / 2f, badgeRect.top + 46f, badgeTextPaint)

        // 8. Title (multi-line safe)
        val contentPadding = 56f
        val contentWidth = (cardRect.width() - contentPadding * 2).toInt()
        val titleStartY = badgeRect.bottom + 36f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8C55B.toInt()
            textSize = 42f
            typeface = arabicUiTypeface
        }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(8f, 1.05f)
            .build()
        canvas.save()
        canvas.translate(cardRect.left + contentPadding, titleStartY)
        titleLayout.draw(canvas)
        canvas.restore()

        // 9. Ornamental Divider
        val dividerY = titleStartY + titleLayout.height + 30f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                width * 0.2f, dividerY, width * 0.8f, dividerY,
                intArrayOf(0x00D4AF37, 0xAAD4AF37.toInt(), 0x00D4AF37),
                null,
                Shader.TileMode.CLAMP
            )
            strokeWidth = 2.5f
        }
        canvas.drawLine(width * 0.2f, dividerY, width * 0.8f, dividerY, dividerPaint)

        // 10. Source Block at Bottom of Card (measured first so body never overlaps)
        var bodyBottomLimit = cardRect.bottom - 56f
        if (source.isNotBlank()) {
            val sourceText = "— $source —"
            val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF7CE8AE.toInt()
                textSize = 30f
                typeface = arabicUiTypeface
            }
            val sourceLayout = StaticLayout.Builder.obtain(sourceText, 0, sourceText.length, sourcePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(6f, 1.05f)
                .build()
            val sourceStartY = cardRect.bottom - 48f - sourceLayout.height
            canvas.save()
            canvas.translate(cardRect.left + contentPadding, sourceStartY)
            sourceLayout.draw(canvas)
            canvas.restore()

            val sepY = sourceStartY - 26f
            canvas.drawLine(width * 0.34f, sepY, width * 0.66f, sepY, dividerPaint)
            bodyBottomLimit = sepY - 30f
        }

        // 11. Main Arabic Ayah / Hadith / Dua Body (auto-fitted from 62sp down to 28sp)
        val bodyTopLimit = dividerY + 34f
        val availableBodyHeight = (bodyBottomLimit - bodyTopLimit).coerceAtLeast(140f)
        val cleanBody = arabicBody.trim()

        var chosenSize = 62f
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = arabicBodyTypeface
            setShadowLayer(4f, 0f, 2f, 0x99000000.toInt())
        }
        var bodyLayout: StaticLayout
        while (true) {
            bodyPaint.textSize = chosenSize
            bodyLayout = StaticLayout.Builder.obtain(cleanBody, 0, cleanBody.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(18f, 1.16f)
                .build()
            if (bodyLayout.height <= availableBodyHeight || chosenSize <= 28f) {
                break
            }
            chosenSize -= 2f
        }

        val bodyStartY = bodyTopLimit + ((availableBodyHeight - bodyLayout.height).coerceAtLeast(0f) / 2f)
        canvas.save()
        canvas.clipRect(
            cardRect.left + 20f,
            bodyTopLimit - 8f,
            cardRect.right - 20f,
            bodyBottomLimit + 8f
        )
        canvas.translate(cardRect.left + contentPadding, bodyStartY)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 12. Clean Footer below Glass Card
        if (cityAndDate.isNotBlank()) {
            val cityDatePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD9FFFFFF.toInt()
                textSize = 26f
                typeface = arabicUiTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cityAndDate, width / 2f, height - 134f, cityDatePaint)
        }

        val footerTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBFF3E5AB.toInt()
            textSize = 22f
            typeface = arabicUiTypeface
            textAlign = Paint.Align.CENTER
        }
        val tagY = if (cityAndDate.isNotBlank()) height - 92f else height - 114f
        canvas.drawText("تطبيق صلاتي وأذكاري  •  شارك الأجر مع من تحب", width / 2f, tagY, footerTagPaint)

        return bitmap
    }

    private fun loadTypefaceSafe(context: Context, fontResId: Int, fallback: Typeface): Typeface {
        return try {
            ResourcesCompat.getFont(context, fontResId) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    internal fun loadAppIconBitmap(context: Context): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_prayer_man)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(128, 128)
        } catch (_: Exception) {
            try {
                ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(128, 128)
            } catch (_: Exception) {
                null
            }
        }
    }
}
