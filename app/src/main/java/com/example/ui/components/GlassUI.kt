package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora animated background: three soft gradient blobs drifting slowly in an
 * 18-second loop, like the Now brief "liquid glass" home. Cheap to render
 * (three radial-gradient circles per frame, GPU-composited) so it stays smooth
 * even on low-end hardware.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()

    val baseColor = if (dark) Color(0xFF040D18) else Color(0xFFF8F4F0)

    val blob1 = if (dark) listOf(Color(0x993B82F6), Color(0x44A78BFA)) else listOf(Color(0xAAFFBBA0), Color(0x55FF9580))
    val blob2 = if (dark) listOf(Color(0x888B5CF6), Color(0x448B5CF6)) else listOf(Color(0x88C4B5FC), Color(0x44A78BFA))
    val blob3 = if (dark) listOf(Color(0x6614B8AA), Color(0x3314B8AA)) else listOf(Color(0x7786EFCA), Color(0x3334D399))

    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(baseColor)

        val t = phase * 2f * Math.PI.toFloat()

        // Top-left warm blob
        val c1 = Offset(
            x = w * (0.20f + 0.08f * sin(t)),
            y = h * (0.16f + 0.06f * cos(t * 0.8f))
        )
        drawCircle(
            brush = Brush.radialGradient(colors = blob1, center = c1, radius = w * 0.75f),
            radius = w * 0.75f,
            center = c1
        )

        // Right lavender blob
        val c2 = Offset(
            x = w * (0.86f + 0.07f * cos(t * 0.9f)),
            y = h * (0.38f + 0.08f * sin(t))
        )
        drawCircle(
            brush = Brush.radialGradient(colors = blob2, center = c2, radius = w * 0.65f),
            radius = w * 0.65f,
            center = c2
        )

        // Bottom mint blob
        val c3 = Offset(
            x = w * (0.42f + 0.10f * sin(t * 1.1f)),
            y = h * (0.92f + 0.05f * cos(t * 0.7f))
        )
        drawCircle(
            brush = Brush.radialGradient(colors = blob3, center = c3, radius = w * 0.8f),
            radius = w * 0.8f,
            center = c3
        )
    }
}

/**
 * Frosted-glass card in the Now brief style: 20 dp radius, translucent
 * surface, subtle 1 dp border, generous inner padding.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(cornerRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = base
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Small-caps section label with a leading emoji, the signature Now brief
 * card heading: 10-11 sp bold, wide letter spacing, uppercase.
 */
@Composable
fun GlassLabel(
    text: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp
        ),
        color = accent,
        modifier = modifier
    )
}

/**
 * Animated progress ring used by the vitals card.
 */
@Composable
fun GlassRing(
    progress: Float,
    ringColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Floating capsule navigation bar in the Now brief style. It floats over the
 * scrolling content (bottom-center) instead of being a full-width bar.
 */
@Composable
fun GlassBottomBar(
    tabs: List<GlassTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedTabIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class GlassTab(
    val label: String,
    val icon: ImageVector
)
