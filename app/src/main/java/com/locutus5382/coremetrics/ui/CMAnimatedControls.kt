package com.locutus5382.coremetrics.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Shared one-shot interaction treatment for all four app finishes.
 *
 * No background timer or infinite shader: animation state changes only while
 * pressed / focused. The same InteractionSource is passed to Material3, so
 * keyboard/gamepad/semantics clicks keep the component's normal behavior.
 */
@Composable
internal fun Modifier.cmButtonFeedback(
    source: MutableInteractionSource, enabled: Boolean = true
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val fraction by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 140), label = "cmPress140")
    val scale = 1f - 0.015f * fraction
    val accent = LocalSurfaceFinish.current.palette.accent
    val focus = if (enabled && focused) 0.035f else 0f
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            val strength = fraction * 0.075f + focus
            if (strength > 0.001f) {
                val r = 12.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = strength.coerceAtMost(0.12f)),
                    cornerRadius = CornerRadius(r, r)
                )
                if (fraction > 0.001f) drawRoundRect(
                    color = accent.copy(alpha = fraction * 0.16f),
                    cornerRadius = CornerRadius(r, r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx()
                    )
                )
            }
        }
}

/*
 * Local overloads deliberately delegate to Material3 without introducing
 * another clickable node. They cover normal app actions and chips in every
 * theme; HUD overlays are native Android Views in a separate :monitor process.
 */
@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, colors = colors,
        modifier = modifier.cmButtonFeedback(source, enabled),
        interactionSource = source, content = content
    )
}

@Composable
internal fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val animated = modifier.cmButtonFeedback(source, enabled)
    if (contentPadding == null) androidx.compose.material3.OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = animated,
        interactionSource = source, content = content
    ) else androidx.compose.material3.OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = animated,
        contentPadding = contentPadding, interactionSource = source,
        content = content
    )
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier.cmButtonFeedback(source, enabled),
        enabled = enabled, interactionSource = source, content = content
    )
}

@Composable
internal fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick, label = label,
        modifier = modifier.cmButtonFeedback(source, enabled),
        enabled = enabled, interactionSource = source
    )
}
