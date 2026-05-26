package com.litontech.netscanner.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litontech.netscanner.ui.components.*
import com.litontech.netscanner.ui.theme.*
import com.litontech.netscanner.viewmodel.*

@Composable
fun SpeedTestScreen(vm: SpeedTestViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshNetworkInfo() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Animated background grid
        BackgroundGrid()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ScreenHeader(title = "網路測速", subtitle = "Speed Test")
            Spacer(Modifier.height(8.dp))

            // Network info pill
            NetworkInfoPill(
                networkType = state.networkType,
                ipAddress   = state.ipAddress
            )
            Spacer(Modifier.height(24.dp))

            // Gauge
            GlassCard(
                modifier  = Modifier.fillMaxWidth(),
                glowColor = CyanPrimary
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Status label
                    val statusText = when (state.state) {
                        TestState.IDLE              -> "準備測速"
                        TestState.PINGING           -> "測量延遲中..."
                        TestState.TESTING_DOWNLOAD  -> "測量下載速度..."
                        TestState.TESTING_UPLOAD    -> "測量上傳速度..."
                        TestState.DONE              -> "測速完成"
                        TestState.ERROR             -> "測速失敗"
                    }
                    Text(
                        text       = statusText,
                        fontSize   = 13.sp,
                        color      = CyanPrimary,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))

                    // Big gauge
                    AnimatedSpeedGauge(
                        speedMbps    = state.currentSpeedMbps,
                        maxSpeedMbps = 1000f,
                        modifier     = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )

                    // Progress bar
                    if (state.state != TestState.IDLE && state.state != TestState.DONE) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress           = { state.progressFraction },
                            modifier           = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color              = CyanPrimary,
                            trackColor         = GaugeEmpty
                        )
                    }

                    // Phase indicator
                    Spacer(Modifier.height(12.dp))
                    PhaseIndicator(currentState = state.state)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Results row
            AnimatedVisibility(
                visible = state.result != null,
                enter   = fadeIn() + expandVertically()
            ) {
                state.result?.let { r ->
                    Column {
                        // Ping + Jitter
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ResultCard(
                                modifier = Modifier.weight(1f),
                                label    = "延遲",
                                value    = "${r.pingMs}",
                                unit     = "ms",
                                icon     = Icons.Filled.Speed,
                                color    = if (r.pingMs < 30) SignalExcellent else if (r.pingMs < 80) SignalGood else SignalFair
                            )
                            ResultCard(
                                modifier = Modifier.weight(1f),
                                label    = "抖動",
                                value    = "${r.jitterMs}",
                                unit     = "ms",
                                icon     = Icons.Filled.Waves,
                                color    = if (r.jitterMs < 10) SignalExcellent else NeonPurple
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // Download + Upload
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ResultCard(
                                modifier = Modifier.weight(1f),
                                label    = "下載",
                                value    = formatSpeed(r.downloadMbps),
                                unit     = speedUnit(r.downloadMbps),
                                icon     = Icons.Filled.ArrowDownward,
                                color    = CyanPrimary
                            )
                            ResultCard(
                                modifier = Modifier.weight(1f),
                                label    = "上傳",
                                value    = formatSpeed(r.uploadMbps),
                                unit     = speedUnit(r.uploadMbps),
                                icon     = Icons.Filled.ArrowUpward,
                                color    = NeonGreen
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Action button
            when (state.state) {
                TestState.IDLE, TestState.DONE, TestState.ERROR -> {
                    StartButton(
                        isRetry = state.state == TestState.DONE || state.state == TestState.ERROR,
                        onClick  = {
                            if (state.state == TestState.DONE || state.state == TestState.ERROR)
                                vm.resetTest()
                            else
                                vm.startTest()
                        }
                    )
                    if (state.state == TestState.IDLE) {
                        LaunchedEffect(Unit) { /* auto-do-nothing */ }
                    }
                    if (state.state == TestState.DONE) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.startTest() }) {
                            Text("重新測速", color = CyanPrimary)
                        }
                    }
                }
                else -> {
                    // Pulsing indicator while testing
                    PulsingTestingIndicator()
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun BackgroundGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 60.dp.toPx()
        for (x in 0..(size.width / step).toInt() + 1) {
            drawLine(
                color       = CyanPrimary.copy(alpha = 0.03f),
                start       = Offset(x * step, 0f),
                end         = Offset(x * step, size.height),
                strokeWidth = 1f
            )
        }
        for (y in 0..(size.height / step).toInt() + 1) {
            drawLine(
                color       = CyanPrimary.copy(alpha = 0.03f),
                start       = Offset(0f, y * step),
                end         = Offset(size.width, y * step),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = title,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary
        )
        Text(
            text     = subtitle,
            fontSize = 11.sp,
            color    = CyanPrimary.copy(alpha = 0.7f),
            letterSpacing = 3.sp
        )
    }
}

@Composable
private fun NetworkInfoPill(networkType: String, ipAddress: String) {
    Row(
        modifier = Modifier
            .background(
                color = CyanFaint,
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = null,
            tint    = CyanPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(networkType, fontSize = 13.sp, color = TextPrimary)
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(BorderBright)
        )
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint    = TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(ipAddress, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun PhaseIndicator(currentState: TestState) {
    val phases = listOf(
        TestState.PINGING           to "延遲",
        TestState.TESTING_DOWNLOAD  to "下載",
        TestState.TESTING_UPLOAD    to "上傳"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { idx, (phaseState, label) ->
            val isDone    = when {
                currentState == TestState.TESTING_DOWNLOAD && phaseState == TestState.PINGING -> true
                currentState == TestState.TESTING_UPLOAD && (phaseState == TestState.PINGING || phaseState == TestState.TESTING_DOWNLOAD) -> true
                currentState == TestState.DONE -> true
                else -> false
            }
            val isCurrent = currentState == phaseState
            val color = when {
                isDone    -> NeonGreen
                isCurrent -> CyanPrimary
                else      -> TextMuted
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(label, fontSize = 12.sp, color = color)
            }
            if (idx < phases.size - 1) {
                Box(Modifier.width(20.dp).height(1.dp).background(BorderGlass))
            }
        }
    }
}

@Composable
private fun ResultCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    GlassCard(modifier = modifier, glowColor = color) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, fontSize = 11.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(unit,  fontSize = 11.sp, color = color, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun StartButton(isRetry: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "btnGlow")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring
        Canvas(modifier = Modifier.size(120.dp)) {
            drawCircle(
                color  = CyanPrimary.copy(alpha = 0.15f * glowRadius),
                radius = size.minDimension / 2f * glowRadius
            )
        }
        // Button
        Button(
            onClick = onClick,
            modifier = Modifier.size(100.dp),
            shape    = CircleShape,
            colors   = ButtonDefaults.buttonColors(
                containerColor = CardBg,
                contentColor   = CyanPrimary
            ),
            border   = BorderStroke(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(CyanPrimary, NeonGreen))
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRetry) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text     = if (isRetry) "重試" else "開始",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PulsingTestingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "testing")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(CyanPrimary.copy(alpha = alpha))
        }
        Text("測速進行中...", fontSize = 14.sp, color = CyanPrimary.copy(alpha = alpha))
    }
}

private fun formatSpeed(mbps: Float): String = when {
    mbps >= 1000f -> String.format("%.2f", mbps / 1000f)
    mbps >= 100f  -> String.format("%.0f", mbps)
    mbps >= 10f   -> String.format("%.1f", mbps)
    else          -> String.format("%.2f", mbps)
}

private fun speedUnit(mbps: Float): String = if (mbps >= 1000f) "Gbps" else "Mbps"
