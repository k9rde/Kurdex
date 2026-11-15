package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the current frosted glass water opacity / blur intensity (0.0f to 1.0f).
 */
val LocalWaterOpacity = staticCompositionLocalOf { 0.65f }

object Glassmorphism {
    // Translucent liquid acrylic layers
    val UltraThinGlass = Color(0x28FFFFFF)
    val ThinGlass = Color(0x35FFFFFF)
    val RegularGlass = Color(0x45FFFFFF)
    val DarkGlassSurface = Color(0x400C2219)
    val DarkGlassSurfaceHigh = Color(0x600C2219)

    // Liquid glass specular highlights border that adapts to blur intensity
    fun dynamicGlassBorder(blurFraction: Float): Brush {
        val f = blurFraction.coerceIn(0.0f, 1.0f)
        val topAlpha = (0.15f + 0.70f * f).coerceIn(0.12f, 0.95f)
        val midAlpha = (0.02f + 0.35f * f).coerceIn(0.02f, 0.40f)
        val goldAlpha = (0.06f + 0.44f * f).coerceIn(0.05f, 0.55f)
        return Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color.White.copy(alpha = midAlpha),
                Color.Transparent,
                Color(0xFFE6C875).copy(alpha = goldAlpha)
            )
        )
    }

    val GlassBorderGradient = dynamicGlassBorder(0.65f)

    val GlassCardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x4D183B2F),
            Color(0x330E261D)
        )
    )

    /**
     * Controls the frosted glass blur and opacity (طمس وضبابية التصميم المائي) from 0% to 100% (0.0f to 1.0f).
     * At 0%: Crystal clear liquid glass (شفافية فائقة ونقاء تام بدون طمس، يظهر الخلفية بوضوح تام).
     * At 100%: Deep frosted acrylic matte glass (أقصى درجات الطمس والضبابية المائية مع تباين قوي ومريح للعين).
     */
    fun dynamicGlassCardGradient(blurFraction: Float): Brush {
        val f = blurFraction.coerceIn(0.0f, 1.0f)
        // Upper layer opacity: from 0.03 (almost 100% transparent crystal) to 0.90 (heavy frosted blur)
        val alphaTop = 0.03f + 0.85f * f
        // Bottom layer opacity: from 0.02 to 0.92
        val alphaBottom = 0.02f + 0.90f * f

        // Tint dark emerald and midnight tones
        val r1 = 0x14
        val g1 = 0x38
        val b1 = 0x2C

        val r2 = 0x08
        val g2 = 0x1A
        val b2 = 0x14

        return Brush.verticalGradient(
            colors = listOf(
                Color(r1, g1, b1).copy(alpha = alphaTop),
                Color(r2, g2, b2).copy(alpha = alphaBottom)
            )
        )
    }

    /**
     * Hero and highlighted prayer cards with rich emerald-gold sheen scaling with blur.
     */
    fun dynamicAccentGlassGradient(blurFraction: Float): Brush {
        val f = blurFraction.coerceIn(0.0f, 1.0f)
        val topAlpha = 0.08f + 0.78f * f
        val bottomAlpha = 0.05f + 0.88f * f
        return Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1B5E4A).copy(alpha = topAlpha),
                Color(0xFF0C2B21).copy(alpha = bottomAlpha)
            )
        )
    }
}

fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    containerBrush: Brush = Glassmorphism.GlassCardGradient,
    borderBrush: Brush = Glassmorphism.GlassBorderGradient
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(containerBrush)
    .border(borderWidth, borderBrush, RoundedCornerShape(cornerRadius))

