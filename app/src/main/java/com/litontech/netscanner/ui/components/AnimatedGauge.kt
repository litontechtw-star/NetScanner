package com.litontech.netscanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litontech.netscanner.ui.theme.*
import kotlin.math.*

@Composable
fun AnimatedSpeedGauge(
    speedMbps: Float,
    maxSpeedMbps: Float = 1000f,
    modifier: Modifier = Modifier,
    label: String = "Mbps"
) {
    // Animate needle from 0 to current speed
    val animatedSpeed by animateFloatAsState(
        targetValue  = speedMbps,
        animationSpec = tween(
            durationMillis = 600,
            easing         = FastOutSlowInEasing
        ),
        label = "speedAnim"
    )

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val fraction = (animatedSpeed / maxSpeedMbps).coerceIn(0f, 1f)

    // Gauge arc colors based on speed
    val gaugeColor = when {
        fraction < 0.3f -> SignalGood
        fraction < 0.6f -> CyanPrimary
        fraction < 0.85f -> NeonPurple
        else -> SignalFair
    }

    Box(
        modifier        = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx    = size.width  / 2f
            val cy    = size.height / 2f * 1.1f  // shift center down slightly
            val radius = (size.width.coerceAtMost(size.height) * 0.42f)

            val startAngle = 150f
            val sweepTotal = 240f
            val arcRect = androidx.compose.ui.geometry.Rect(
                center = Offset(cx, cy),
                radius = radius
            )
            val topLeft    = Offset(cx - radius, cy - radius)
            val arcSize    = Size(radius * 2, radius * 2)
            val strokeWidth = radius * 0.10f

            // ─── Background track ─────────────────────────────
            drawArc(
                color       = GaugeEmpty,
                startAngle  = startAngle,
                sweepAngle  = sweepTotal,
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcSize,
                style       = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // ─── Tick marks ───────────────────────────────────
            val tickCount = 10
            for (i in 0..tickCount) {
                val tickFraction = i.toFloat() / tickCount
                val tickAngleDeg = startAngle + sweepTotal * tickFraction
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val outerR  = radius + strokeWidth * 0.2f
                val innerR  = radius - strokeWidth * 0.5f
                val isMajor = i % 2 == 0
                val tickR   = if (isMajor) innerR - strokeWidth * 0.4f else innerR
                drawLine(
                    color       = TextMuted.copy(alpha = 0.5f),
                    start       = Offset(
                        cx + outerR * cos(tickAngleRad).toFloat(),
                        cy + outerR * sin(tickAngleRad).toFloat()
                    ),
                    end         = Offset(
                        cx + tickR * cos(tickAngleRad).toFloat(),
                        cy + tickR * sin(tickAngleRad).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2f else 1f
                )
            }

            // ─── Glow layer (blurred arc illusion) ────────────
            if (fraction > 0f) {
                val glowBrush = Brush.sweepGradient(
                    colorStops = arrayOf(
                        0f       to Color.Transparent,
                        fraction to gaugeColor.copy(alpha = glowAlpha * 0.4f)
                    ),
                    center = Offset(cx, cy)
                )
                drawArc(
                    brush      = Brush.sweepGradient(
                        0f to Color.Transparent,
                        fraction * (sweepTotal / 360f) to gaugeColor.copy(alpha = 0.25f)
                    ),
                    startAngle  = startAngle,
                    sweepAngle  = sweepTotal * fraction,
                    useCenter   = false,
                    topLeft     = Offset(cx - radius - strokeWidth, cy - radius - strokeWidth),
                    size        = Size(radius * 2 + strokeWidth * 2, radius * 2 + strokeWidth * 2),
                    style       = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round)
                )
            }

            // ─── Active arc (coloured progress) ───────────────
            if (fraction > 0f) {
                val gradient = Brush.sweepGradient(
                    colors = listOf(GaugeStart, GaugeMid, gaugeColor),
                    center = Offset(cx, cy)
                )
                drawArc(
                    brush       = gradient,
                    startAngle  = startAngle,
                    sweepAngle  = sweepTotal * fraction,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = arcSize,
                    style       = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // ─── Needle ───────────────────────────────────────
            val needleAngleDeg = startAngle + sweepTotal * fraction
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
            val needleLen      = radius * 0.78f
            val needleTip      = Offset(
                cx + needleLen * cos(needleAngleRad).toFloat(),
                cy + needleLen * sin(needleAngleRad).toFloat()
            )
            val needleBase     = Offset(
                cx - radius * 0.12f * cos(needleAngleRad).toFloat(),
                cy - radius * 0.12f * sin(needleAngleRad).toFloat()
            )
            // Needle shadow
            drawLine(
                color       = gaugeColor.copy(alpha = 0.3f),
                start       = needleBase,
                end         = needleTip,
                strokeWidth = strokeWidth * 0.35f,
                cap         = StrokeCap.Round
            )
            // Needle
            drawLine(
                color       = GaugeNeedle,
                start       = needleBase,
                end         = needleTip,
                strokeWidth = strokeWidth * 0.18f,
                cap         = StrokeCap.Round
            )

            // ─── Center hub ──────────────────────────────────
            drawCircle(
                color  = gaugeColor,
                radius = strokeWidth * 0.45f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color  = DeepSpace,
                radius = strokeWidth * 0.30f,
                center = Offset(cx, cy)
            )
        }

        // Speed value text overlay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text       = if (animatedSpeed < 1f) "0" else
                    if (animatedSpeed >= 1000f) String.format("%.1f", animatedSpeed / 1000f)
                    else String.format("%.1f", animatedSpeed),
                fontSize   = 42.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Text(
                text     = if (animatedSpeed >= 1000f) "Gbps" else label,
                fontSize = 14.sp,
                color    = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Animated radar sweep background for WiFi scan screen
// ─────────────────────────────────────────────────────────
@Composable
fun RadarSweepCanvas(
    modifier: Modifier = Modifier,
    isScanning: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 0.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        if (!isScanning) return@Canvas
        val cx     = size.width  / 2f
        val cy     = size.height / 2f
        val maxR   = size.width.coerceAtMost(size.height) / 2f * 0.95f

        // Concentric rings
        for (i in 1..4) {
            val r = maxR * (i / 4f)
            drawCircle(
                color  = CyanPrimary.copy(alpha = 0.08f),
                radius = r,
                center = Offset(cx, cy),
                style  = Stroke(width = 1f)
            )
        }

        // Crosshair lines
        drawLine(CyanPrimary.copy(alpha = 0.08f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), 1f)
        drawLine(CyanPrimary.copy(alpha = 0.08f), Offset(cx, cy - maxR), Offset(cx, cy + maxR), 1f)

        // Sweep gradient
        rotate(sweepAngle, Offset(cx, cy)) {
            val sweepBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f  to Color.Transparent,
                    0.85f to Color.Transparent,
                    1.0f  to CyanPrimary.copy(alpha = 0.35f)
                ),
                center = Offset(cx, cy)
            )
            drawCircle(
                brush  = sweepBrush,
                radius = maxR,
                center = Offset(cx, cy)
            )
        }

        // Pulse ring at sweep tip
        val sweepRad    = Math.toRadians((sweepAngle - 90f).toDouble())
        val pulseTipX   = cx + maxR * cos(sweepRad).toFloat()
        val pulseTipY   = cy + maxR * sin(sweepRad).toFloat()
        drawCircle(
            color  = CyanPrimary.copy(alpha = pulseAlpha * 0.5f),
            radius = maxR * 0.08f,
            center = Offset(pulseTipX, pulseTipY)
        )
    }
}

// ─────────────────────────────────────────────────────────
// Signal strength bars component
// ─────────────────────────────────────────────────────────
@Composable
fun SignalBarsIcon(
    rssi: Int,
    modifier: Modifier = Modifier,
    filledColor: Color  = CyanPrimary,
    emptyColor: Color   = TextMuted
) {
    val bars  = 5
    val level = when {
        rssi >= -50 -> 5
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else        -> 0
    }
    Row(
        modifier            = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment   = Alignment.Bottom
    ) {
        for (i in 1..bars) {
            val barHeight = (4 + i * 4).dp
            val filled    = i <= level
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        color = if (filled) filledColor else emptyColor.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}
