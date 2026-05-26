package com.litontech.netscanner.ui.screens

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
import com.google.accompanist.permissions.*
import com.litontech.netscanner.ui.components.*
import com.litontech.netscanner.ui.theme.*
import com.litontech.netscanner.viewmodel.WifiViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SignalQualityScreen(vm: WifiViewModel = viewModel()) {
    val state by vm.signalState.collectAsStateWithLifecycle()

    val locationPermission = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    DisposableEffect(Unit) {
        vm.startMonitoring()
        onDispose { vm.stopMonitoring() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenHeader(title = "訊號品質", subtitle = "Signal Quality")
            Spacer(Modifier.height(16.dp))

            if (!state.isConnected) {
                // Not connected placeholder
                NotConnectedCard()
            } else {
                val info = state.current

                // ── Big signal indicator ─────────────────────────────
                BigSignalIndicator(rssi = info.rssi, quality = info.qualityPercent)
                Spacer(Modifier.height(20.dp))

                // ── SSID + BSSID ─────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = CyanPrimary) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CyanFaint, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Wifi, null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = info.ssid.ifBlank { "已連線 Wi-Fi" },
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = TextPrimary
                            )
                            Text(
                                text     = info.bssid.lowercase().ifBlank { "──" },
                                fontSize = 12.sp,
                                color    = TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(info.band, fontSize = 13.sp, color = CyanPrimary, fontWeight = FontWeight.Medium)
                            Text("Ch ${info.channel}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── RSSI live graph ──────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = CyanPrimary) {
                    Column {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("訊號強度歷史", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${info.rssi} dBm", fontSize = 14.sp, color = CyanPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        RssiGraph(
                            history  = state.rssiHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        )
                        // dBm scale
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-40 dBm", fontSize = 9.sp, color = TextMuted)
                            Text("強", fontSize = 9.sp, color = SignalExcellent)
                            Text("-100 dBm", fontSize = 9.sp, color = TextMuted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── Speed info ──────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        label    = "連結速度",
                        value    = "${info.linkSpeedMbps}",
                        unit     = "Mbps",
                        icon     = Icons.Filled.Speed,
                        color    = CyanPrimary
                    )
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        label    = "頻率",
                        value    = "${info.frequencyMHz}",
                        unit     = "MHz",
                        icon     = Icons.Filled.Waves,
                        color    = NeonPurple
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ── TX / RX speeds ──────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        label    = "傳送速率 (TX)",
                        value    = "${info.txLinkSpeed}",
                        unit     = "Mbps",
                        icon     = Icons.Filled.ArrowUpward,
                        color    = NeonGreen
                    )
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        label    = "接收速率 (RX)",
                        value    = "${info.rxLinkSpeed}",
                        unit     = "Mbps",
                        icon     = Icons.Filled.ArrowDownward,
                        color    = CyanPrimary
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ── IP address ──────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = NeonGreen) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Language, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            Text("IP 位址", fontSize = 13.sp, color = TextSecondary)
                        }
                        Text(
                            text       = info.ipAddress,
                            fontSize   = 14.sp,
                            color      = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun BigSignalIndicator(rssi: Int, quality: Int) {
    val signalColor = when {
        rssi >= -50 -> SignalExcellent
        rssi >= -60 -> SignalGood
        rssi >= -70 -> SignalFair
        rssi >= -80 -> SignalWeak
        else        -> SignalNone
    }
    val qualityLabel = when {
        rssi >= -50 -> "極強"
        rssi >= -60 -> "強"
        rssi >= -70 -> "良好"
        rssi >= -80 -> "弱"
        rssi >= -90 -> "很弱"
        else        -> "無訊號"
    }

    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "signal")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 0.2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        // Outer pulse ring
        Canvas(modifier = Modifier.size(180.dp)) {
            drawCircle(
                color  = signalColor.copy(alpha = pulseAlpha * 0.4f),
                radius = size.minDimension / 2f * pulseScale
            )
        }
        // Inner card
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(signalColor.copy(alpha = 0.15f), CardBg)
                    ),
                    shape = CircleShape
                )
                .border(2.dp, signalColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "${rssi} dBm",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                SignalBarsIcon(
                    rssi        = rssi,
                    filledColor = signalColor,
                    modifier    = Modifier.height(20.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = qualityLabel,
                    fontSize   = 14.sp,
                    color      = signalColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text     = "$quality%",
                    fontSize = 11.sp,
                    color    = TextMuted
                )
            }
        }
    }
}

@Composable
private fun RssiGraph(history: List<Int>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (history.isEmpty()) return@Canvas
        val minRssi = -100f
        val maxRssi = -30f
        val range   = maxRssi - minRssi
        val pts     = history.takeLast(60)
        val w       = size.width
        val h       = size.height
        val step    = w / (pts.size - 1).coerceAtLeast(1).toFloat()

        // Grid lines at -50, -70, -90
        listOf(-50f, -70f, -90f).forEach { level ->
            val y = h * (1f - (level - minRssi) / range)
            drawLine(
                color       = CyanPrimary.copy(alpha = 0.1f),
                start       = Offset(0f, y),
                end         = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Fill gradient under curve
        if (pts.size >= 2) {
            val path = Path()
            pts.forEachIndexed { i, rssi ->
                val x = i * step
                val y = h * (1f - (rssi - minRssi) / range).coerceIn(0f, 1f)
                if (i == 0) path.moveTo(x, h) else Unit
                if (i == 0) path.lineTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo((pts.size - 1) * step, h)
            path.close()
            drawPath(
                path  = path,
                brush = Brush.verticalGradient(
                    colors = listOf(CyanPrimary.copy(alpha = 0.3f), Color.Transparent)
                )
            )

            // Line
            val linePath = Path()
            pts.forEachIndexed { i, rssi ->
                val x = i * step
                val y = h * (1f - (rssi - minRssi) / range).coerceIn(0f, 1f)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            drawPath(
                path       = linePath,
                color      = CyanPrimary,
                style      = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Last point dot
            val lastRssi = pts.last()
            val lastX    = (pts.size - 1) * step
            val lastY    = h * (1f - (lastRssi - minRssi) / range).coerceIn(0f, 1f)
            drawCircle(CyanPrimary, radius = 4f, center = Offset(lastX, lastY))
        }
    }
}

@Composable
private fun InfoTile(
    modifier: Modifier,
    label:    String,
    value:    String,
    unit:     String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    color:    Color
) {
    GlassCard(modifier = modifier, glowColor = color, cornerRadius = 12.dp) {
        Column {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Text(label, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(unit,  fontSize = 10.sp, color = color, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun NotConnectedCard() {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint     = TextMuted,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("未連線到 Wi-Fi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "請連線到 Wi-Fi 網路以查看訊號品質資訊",
            fontSize = 14.sp,
            color    = TextMuted
        )
    }
}
