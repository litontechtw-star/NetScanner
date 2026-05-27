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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litontech.netscanner.ui.components.GlassCard
import com.litontech.netscanner.ui.theme.*
import com.litontech.netscanner.viewmodel.DirectionViewModel
import kotlin.math.*

@Composable
fun DirectionFinderScreen(vm: DirectionViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Stop sensors when navigating away
    DisposableEffect(Unit) {
        onDispose { vm.stopScanning() }
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
            ScreenHeader(title = "訊號定向", subtitle = "Direction Finder")
            Spacer(Modifier.height(8.dp))

            // ── Target AP info card ──────────────────────────────────────────
            GlassCard(
                modifier  = Modifier.fillMaxWidth(),
                glowColor = if (state.wifiConnected) NeonGreen else SignalFair
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("追蹤 AP", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text       = if (state.wifiConnected) state.targetSsid else "未連接 WiFi",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (state.wifiConnected) NeonGreen else SignalFair
                        )
                        if (state.targetBssid.isNotEmpty()) {
                            Text(state.targetBssid, fontSize = 10.sp, color = TextMuted)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("目前訊號", fontSize = 11.sp, color = TextSecondary)
                        if (state.wifiConnected && state.isScanning) {
                            Text(
                                text       = "${state.currentRssi} dBm",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = rssiColor(state.currentRssi)
                            )
                            Text(
                                text     = rssiLabel(state.currentRssi),
                                fontSize = 11.sp,
                                color    = rssiColor(state.currentRssi)
                            )
                        } else {
                            Text("--", fontSize = 18.sp, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Compass polar chart ──────────────────────────────────────────
            GlassCard(
                modifier  = Modifier.fillMaxWidth(),
                glowColor = CyanPrimary
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            !state.isScanning          -> "點擊下方按鈕開始掃描"
                            !state.wifiConnected       -> "請先連接 WiFi"
                            state.sampleCount < 10     -> "請緩慢水平旋轉手機一整圈"
                            else                       -> "繼續旋轉以提高精確度（${state.sampleCount} 筆）"
                        },
                        fontSize     = 12.sp,
                        color        = CyanPrimary.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    // Polar chart canvas
                    CompassPolarChart(
                        azimuthDeg   = state.azimuthDeg,
                        directionMap = state.directionMap,
                        bestDirDeg   = state.bestDirDeg,
                        isScanning   = state.isScanning,
                        modifier     = Modifier.size(280.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Stats row
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBadge(
                            label = "方位",
                            value = "${state.azimuthDeg.toInt()}°",
                            sub   = compassLabel(state.azimuthDeg)
                        )
                        StatBadge(
                            label = "樣本",
                            value = "${state.sampleCount}",
                            sub   = "筆記錄"
                        )
                        StatBadge(
                            label = "最強方向",
                            value = state.bestDirDeg?.let { "${it}°" } ?: "--",
                            sub   = state.bestDirDeg?.let { compassLabel(it.toFloat()) } ?: "尚未確定"
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Best direction guidance card ─────────────────────────────────
            if (state.bestDirDeg != null && state.directionMap.isNotEmpty()) {
                val bestBucket = (state.bestDirDeg!! / 10) % 36
                val bestRssi   = state.directionMap[bestBucket] ?: -100
                DirectionGuidanceCard(
                    bestDeg        = state.bestDirDeg!!,
                    bestRssi       = bestRssi,
                    currentAzimuth = state.azimuthDeg
                )
                Spacer(Modifier.height(12.dp))
            }

            // ── Controls ─────────────────────────────────────────────────────
            if (!state.isScanning) {
                Button(
                    onClick  = { vm.startScanning() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = CyanFaint,
                        contentColor   = CyanPrimary
                    ),
                    border   = BorderStroke(1.5.dp, CyanPrimary)
                ) {
                    Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("開始定向掃描", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (state.directionMap.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.resetScan() }) {
                        Text("清除記錄重新掃描", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                OutlinedButton(
                    onClick  = { vm.stopScanning() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                    border   = BorderStroke(1.5.dp, NeonOrange)
                ) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("停止掃描", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── How-to instructions ──────────────────────────────────────────
            HowToCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Compass polar chart ──────────────────────────────────────────────────────
@Composable
private fun CompassPolarChart(
    azimuthDeg:   Float,
    directionMap: Map<Int, Int>,
    bestDirDeg:   Int?,
    isScanning:   Boolean,
    modifier:     Modifier = Modifier
) {
    // Blinking dot for current heading indicator
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label         = "dot"
    )

    Canvas(modifier = modifier) {
        val cx   = size.width / 2f
        val cy   = size.height / 2f
        val maxR = size.minDimension / 2f * 0.80f

        // ── Grid rings ──
        val gridColors = listOf(0.06f, 0.12f, 0.12f, 0.10f)
        for (ring in 1..4) {
            drawCircle(
                color  = CyanPrimary.copy(alpha = gridColors[ring - 1]),
                radius = maxR * ring / 4f,
                center = Offset(cx, cy),
                style  = Stroke(1f)
            )
        }

        // ── Direction spoke lines ──
        for (deg in 0 until 360 step 45) {
            val rad = Math.toRadians((deg - 90.0)).toFloat()
            drawLine(
                color       = CyanPrimary.copy(alpha = 0.08f),
                start       = Offset(cx, cy),
                end         = Offset(cx + maxR * cos(rad), cy + maxR * sin(rad)),
                strokeWidth = 1f
            )
        }

        // ── Signal polar fill (blob) ──
        if (directionMap.isNotEmpty()) {
            val path = Path()
            var firstPoint = true
            for (bucket in 0..35) {
                val rssi       = directionMap[bucket] ?: -95
                val normalized = ((rssi + 90f) / 60f).coerceIn(0f, 1f)
                val r          = maxR * 0.08f + maxR * 0.92f * normalized
                val angleDeg   = bucket * 10f - 90f
                val angleRad   = Math.toRadians(angleDeg.toDouble()).toFloat()
                val px         = cx + r * cos(angleRad)
                val py         = cy + r * sin(angleRad)
                if (firstPoint) { path.moveTo(px, py); firstPoint = false }
                else path.lineTo(px, py)
            }
            path.close()

            // Determine color based on best RSSI
            val bestRssi  = directionMap.values.maxOrNull() ?: -90
            val fillColor = rssiComposeColor(bestRssi)

            drawPath(path, fillColor.copy(alpha = 0.18f))
            drawPath(path, fillColor.copy(alpha = 0.75f), style = Stroke(2.5f))
        }

        // ── Best direction arrow (green) ──
        bestDirDeg?.let { deg ->
            val rad     = Math.toRadians((deg - 90.0)).toFloat()
            val arrowR  = maxR * 0.70f
            val tipX    = cx + arrowR * cos(rad)
            val tipY    = cy + arrowR * sin(rad)

            // Arrow glow effect
            drawLine(NeonGreen.copy(alpha = 0.25f), Offset(cx, cy), Offset(tipX, tipY), strokeWidth = 8f)
            // Arrow shaft
            drawLine(NeonGreen, Offset(cx, cy), Offset(tipX, tipY), strokeWidth = 3f)

            // Arrowhead wings
            val wingLen  = maxR * 0.10f
            val wingAng1 = rad + Math.toRadians(150.0).toFloat()
            val wingAng2 = rad - Math.toRadians(150.0).toFloat()
            drawLine(NeonGreen, Offset(tipX, tipY),
                Offset(tipX + wingLen * cos(wingAng1), tipY + wingLen * sin(wingAng1)), strokeWidth = 3f)
            drawLine(NeonGreen, Offset(tipX, tipY),
                Offset(tipX + wingLen * cos(wingAng2), tipY + wingLen * sin(wingAng2)), strokeWidth = 3f)
        }

        // ── Current heading line (dashed cyan) ──
        val headRad = Math.toRadians((azimuthDeg - 90.0)).toFloat()
        drawLine(
            color       = CyanPrimary,
            start       = Offset(cx, cy),
            end         = Offset(cx + maxR * cos(headRad), cy + maxR * sin(headRad)),
            strokeWidth = 1.5f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        // Heading tip blinking dot
        if (isScanning) {
            val tipX = cx + maxR * cos(headRad)
            val tipY = cy + maxR * sin(headRad)
            drawCircle(CyanPrimary.copy(alpha = dotAlpha), radius = 5f, center = Offset(tipX, tipY))
        }

        // ── Center dot ──
        drawCircle(DeepSpace,    radius = 10f, center = Offset(cx, cy))
        drawCircle(CyanPrimary,  radius = 7f,  center = Offset(cx, cy))
        drawCircle(TextPrimary,  radius = 3f,  center = Offset(cx, cy))

        // ── Compass labels ──
        drawIntoCanvas { canvas ->
            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
                textAlign   = android.graphics.Paint.Align.CENTER
            }
            val lblText  = arrayOf("N",  "E",  "S",  "W",  "NE", "SE",  "SW",  "NW")
            val lblDeg   = floatArrayOf(0f, 90f, 180f, 270f, 45f, 135f, 225f, 315f)
            val lblAlpha = floatArrayOf(1.0f, 0.7f, 0.7f, 0.7f, 0.4f, 0.4f, 0.4f, 0.4f)
            val lblSize  = floatArrayOf(36f, 28f, 28f, 28f, 22f, 22f, 22f, 22f)
            val labelR   = maxR * 1.13f
            for (i in lblText.indices) {
                val rad = Math.toRadians((lblDeg[i] - 90.0)).toFloat()
                val lx  = cx + labelR * cos(rad)
                val ly  = cy + labelR * sin(rad) + lblSize[i] * 0.38f
                labelPaint.color    = android.graphics.Color.argb(
                    (255 * lblAlpha[i]).toInt(), 0, 200, 230
                )
                labelPaint.textSize = lblSize[i]
                canvas.nativeCanvas.drawText(lblText[i], lx, ly, labelPaint)
            }
        }
    }
}

// ─── Best direction guidance card ────────────────────────────────────────────
@Composable
private fun DirectionGuidanceCard(bestDeg: Int, bestRssi: Int, currentAzimuth: Float) {
    // Calculate how many degrees to turn
    val diff   = ((bestDeg - currentAzimuth + 540) % 360) - 180   // -180 to +180
    val turnDir = if (diff >= 0) "向右轉 ${diff.toInt()}°" else "向左轉 ${(-diff).toInt()}°"

    GlassCard(
        modifier  = Modifier.fillMaxWidth(),
        glowColor = NeonGreen
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("最強訊號方向", fontSize = 11.sp, color = TextSecondary, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Big bearing display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$bestDeg°",
                        fontSize   = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NeonGreen
                    )
                    Text(
                        text     = compassLabel(bestDeg.toFloat()),
                        fontSize = 14.sp,
                        color    = NeonGreen.copy(alpha = 0.8f)
                    )
                }

                // Divider
                Box(Modifier.width(1.dp).height(60.dp).background(BorderBright))

                // Turn instruction
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        tint     = CyanPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = turnDir,
                        fontSize = 13.sp,
                        color    = CyanPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text     = "朝此方向前進",
                        fontSize = 11.sp,
                        color    = TextSecondary
                    )
                }

                // Divider
                Box(Modifier.width(1.dp).height(60.dp).background(BorderBright))

                // Best RSSI
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$bestRssi",
                        fontSize   = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color      = rssiComposeColor(bestRssi)
                    )
                    Text(
                        text     = "dBm 峰值",
                        fontSize = 11.sp,
                        color    = TextSecondary
                    )
                }
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────
@Composable
private fun StatBadge(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(sub,   fontSize = 10.sp, color = CyanPrimary.copy(alpha = 0.7f))
    }
}

@Composable
private fun HowToCard() {
    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = NeonPurple) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, null,
                    tint = NeonPurple, modifier = Modifier.size(16.dp))
                Text("使用方法", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonPurple)
            }
            val steps = listOf(
                "① 先連上你想定位的 WiFi，再點「開始定向掃描」",
                "② 將手機水平握持（螢幕朝上），緩慢轉一整圈（360°）",
                "③ 綠色箭頭指向訊號最強的方向 → 基地台就在那裡",
                "④ 往綠色方向走，訊號 dBm 數字上升表示你在靠近",
                "⑤ 重複掃描可逐步縮小目標範圍，找到隱藏基地台位置"
            )
            steps.forEach { step ->
                Text(step, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
private fun rssiComposeColor(rssi: Int): Color = when {
    rssi >= -55 -> SignalExcellent
    rssi >= -65 -> SignalGood
    rssi >= -75 -> SignalFair
    else        -> SignalWeak
}

private fun rssiColor(rssi: Int): Color = rssiComposeColor(rssi)

private fun rssiLabel(rssi: Int): String = when {
    rssi >= -55 -> "極強"
    rssi >= -65 -> "良好"
    rssi >= -75 -> "普通"
    rssi >= -85 -> "弱"
    else        -> "極弱"
}

private fun compassLabel(deg: Float): String {
    val d = ((deg % 360) + 360) % 360
    return when {
        d < 22.5  -> "北"
        d < 67.5  -> "東北"
        d < 112.5 -> "東"
        d < 157.5 -> "東南"
        d < 202.5 -> "南"
        d < 247.5 -> "西南"
        d < 292.5 -> "西"
        d < 337.5 -> "西北"
        else      -> "北"
    }
}
