package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Glassmorphism
import com.example.ui.theme.LocalWaterOpacity
import com.example.ui.theme.liquidGlass

/**
 * High-refresh rate 120Hz/90Hz/60Hz physics-based spring interactive liquid glass card.
 * Automatically adapts to the user's chosen water frosted blur / opacity (0% to 100%) from Settings.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerBrush: Brush? = null,
    borderBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val currentBlur = LocalWaterOpacity.current
    val actualContainerBrush = containerBrush ?: Glassmorphism.dynamicGlassCardGradient(currentBlur)
    val actualBorderBrush = borderBrush ?: Glassmorphism.dynamicGlassBorder(currentBlur)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(
                0.965f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        } else {
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale.value)
            .liquidGlass(
                cornerRadius = cornerRadius,
                containerBrush = actualContainerBrush,
                borderBrush = actualBorderBrush
            )
            .then(clickModifier),
        content = content
    )
}
