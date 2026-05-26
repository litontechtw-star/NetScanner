package com.litontech.netscanner.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.litontech.netscanner.ui.components.*
import com.litontech.netscanner.ui.theme.*
import com.litontech.netscanner.viewmodel.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WifiScanScreen(vm: WifiViewModel = viewModel()) {
    val state by vm.scanState.collectAsStateWithLifecycle()

    val locationPermission = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ScreenHeader(title = "WiFi 掃描", subtitle = "Network Scanner")
            Spacer(Modifier.height(12.dp))

            // Radar + stats row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Radar animation
                GlassCard(
                    modifier  = Modifier.size(140.dp),
                    glowColor = CyanPrimary
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RadarSweepCanvas(
                            modifier   = Modifier.fillMaxSize(),
                            isScanning = state.isScanning
                        )
                        if (!state.isScanning) {
                            Icon(
                                imageVector = Icons.Filled.WifiFind,
                                contentDescription = null,
                                tint     = CyanPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Stats column
                Column(
                    modifier              = Modifier.weight(1f),
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    StatChip(
                        label = "發現網路",
                        value = "${state.networks.size}",
                        icon  = Icons.Filled.NetworkCheck,
                        color = CyanPrimary
                    )
                    StatChip(
                        label = "掃描次數",
                        value = "${state.scanCount}",
                        icon  = Icons.Filled.Refresh,
                        color = NeonGreen
                    )
                    val fiveGCount = state.networks.count { it.frequency >= 5000 }
                    StatChip(
                        label = "5GHz 網路",
                        value = "$fiveGCount",
                        icon  = Icons.Filled.Router,
                        color = NeonPurple
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sort + scan controls
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Sort buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortChip("訊號", WifiSortOption.SIGNAL,  state.sortBy, vm::setSortOption)
                    SortChip("名稱", WifiSortOption.SSID,    state.sortBy, vm::setSortOption)
                    SortChip("頻段", WifiSortOption.BAND,    state.sortBy, vm::setSortOption)
                }
                // Scan button
                OutlinedButton(
                    onClick  = {
                        if (locationPermission.allPermissionsGranted) vm.startScan()
                        else locationPermission.launchMultiplePermissionRequest()
                    },
                    enabled  = !state.isScanning,
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, CyanPrimary.copy(alpha = if (state.isScanning) 0.3f else 1f)),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary)
                ) {
                    if (state.isScanning) {
                        val rotation by rememberInfiniteTransition(label = "rot").animateFloat(
                            initialValue  = 0f,
                            targetValue   = 360f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                            label         = "rotation"
                        )
                        Icon(
                            Icons.Filled.Refresh,
                            null,
                            modifier = Modifier.size(16.dp).rotate(rotation)
                        )
                    } else {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isScanning) "掃描中" else "掃描", fontSize = 13.sp)
                }
            }

            if (state.errorMsg.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.errorMsg, fontSize = 11.sp, color = SignalFair)
            }

            Spacer(Modifier.height(8.dp))
            NeonDivider()
            Spacer(Modifier.height(8.dp))

            // Permission request banner
            if (!locationPermission.allPermissionsGranted) {
                PermissionBanner(
                    onRequest = { locationPermission.launchMultiplePermissionRequest() }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Network list
            if (state.networks.isEmpty() && !state.isScanning) {
                EmptyListPlaceholder(
                    hasPermission = locationPermission.allPermissionsGranted,
                    onScan        = {
                        if (locationPermission.allPermissionsGranted) vm.startScan()
                        else locationPermission.launchMultiplePermissionRequest()
                    }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding      = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(state.networks, key = { _, n -> n.bssid }) { index, network ->
                        AnimatedVisibility(
                            visible = true,
                            enter   = fadeIn(tween(200 + index * 30)) + slideInHorizontally(
                                tween(200 + index * 30)
                            )
                        ) {
                            WifiNetworkCard(network = network)
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun StatChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    GlassCard(
        modifier  = Modifier.fillMaxWidth(),
        glowColor = color,
        cornerRadius = 12.dp
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(label, fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SortChip(
    label:    String,
    option:   WifiSortOption,
    selected: WifiSortOption,
    onSelect: (WifiSortOption) -> Unit
) {
    val isSelected = option == selected
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) CyanPrimary.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isSelected) CyanPrimary else BorderGlass,
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect(option) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = if (isSelected) CyanPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun WifiNetworkCard(network: WifiNetwork) {
    val bandColor = when (network.band) {
        "6 GHz" -> NeonPurple
        "5 GHz" -> CyanPrimary
        else    -> NeonGreen
    }
    val signalColor = when {
        network.rssi >= -50 -> SignalExcellent
        network.rssi >= -60 -> SignalGood
        network.rssi >= -70 -> SignalFair
        else                -> SignalWeak
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = signalColor.copy(alpha = 0.5f)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Signal bars
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SignalBarsIcon(
                    rssi        = network.rssi,
                    filledColor = signalColor,
                    modifier    = Modifier.height(24.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = "${network.rssi} dBm",
                    fontSize = 9.sp,
                    color    = signalColor
                )
            }

            // Main info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = network.ssid.ifBlank { "(隱藏 SSID)" },
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary,
                    maxLines   = 1
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTag(text = network.band, color = bandColor)
                    InfoTag(text = "Ch ${network.channel}", color = TextSecondary)
                    InfoTag(text = network.securityType, color = NeonPurple)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = network.bssid.lowercase(),
                    fontSize = 10.sp,
                    color    = TextMuted
                )
            }

            // Quality indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QualityRing(
                    percent = network.qualityPercent,
                    color   = signalColor
                )
                Text(
                    text     = network.qualityLabel,
                    fontSize = 9.sp,
                    color    = signalColor
                )
            }
        }
    }
}

@Composable
private fun InfoTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}

@Composable
private fun QualityRing(percent: Int, color: Color) {
    Canvas(modifier = Modifier.size(36.dp)) {
        val stroke = 4f
        val pad    = stroke / 2
        drawArc(
            color       = GaugeEmpty,
            startAngle  = -90f,
            sweepAngle  = 360f,
            useCenter   = false,
            topLeft     = Offset(pad, pad),
            size        = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style       = Stroke(stroke)
        )
        drawArc(
            color       = color,
            startAngle  = -90f,
            sweepAngle  = 360f * (percent / 100f),
            useCenter   = false,
            topLeft     = Offset(pad, pad),
            size        = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style       = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = SignalFair) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.LocationOff, null, tint = SignalFair, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("需要位置權限", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text("Android 需要位置權限才能掃描 WiFi 網路", fontSize = 11.sp, color = TextSecondary)
            }
            TextButton(onClick = onRequest) {
                Text("授予", color = CyanPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyListPlaceholder(hasPermission: Boolean, onScan: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WifiFind,
            contentDescription = null,
            tint     = CyanPrimary.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text     = if (hasPermission) "尚未掃描" else "需要位置授權",
            fontSize = 16.sp,
            color    = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = if (hasPermission) "點擊掃描按鈕開始搜尋附近的 WiFi 網路" else "請授予位置權限以使用 WiFi 掃描功能",
            fontSize = 13.sp,
            color    = TextMuted
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick  = onScan,
            shape    = RoundedCornerShape(12.dp),
            border   = BorderStroke(1.dp, CyanPrimary),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary)
        ) {
            Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hasPermission) "開始掃描" else "授予權限並掃描")
        }
    }
}
