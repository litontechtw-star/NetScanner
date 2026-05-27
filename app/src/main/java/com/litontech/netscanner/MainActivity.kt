package com.litontech.netscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.litontech.netscanner.ui.screens.*
import com.litontech.netscanner.ui.theme.*
import com.litontech.netscanner.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetScannerTheme {
                NetScannerApp()
            }
        }
    }
}

// ─── Navigation destinations ─────────────────────────────────────────────────
sealed class Screen(
    val route:    String,
    val label:    String,
    val icon:     ImageVector,
    val iconSel:  ImageVector
) {
    object SpeedTest  : Screen("speed_test",  "測速",       Icons.Filled.Speed,            Icons.Filled.Speed)
    object WifiScan   : Screen("wifi_scan",   "WiFi",       Icons.Filled.WifiFind,         Icons.Filled.WifiFind)
    object Signal     : Screen("signal",      "訊號",       Icons.Filled.SignalCellularAlt, Icons.Filled.SignalCellularAlt)
    object Direction  : Screen("direction",   "定向",       Icons.Filled.Explore,          Icons.Filled.Explore)
}

val navScreens = listOf(Screen.SpeedTest, Screen.WifiScan, Screen.Signal, Screen.Direction)

// ─── Root Composable ──────────────────────────────────────────────────────────
@Composable
fun NetScannerApp() {
    val navController   = rememberNavController()
    val speedTestVm: SpeedTestViewModel  = viewModel()
    val wifiVm:      WifiViewModel       = viewModel()
    val directionVm: DirectionViewModel  = viewModel()

    Scaffold(
        containerColor = DeepSpace,
        contentColor   = TextPrimary,
        bottomBar = {
            NetScannerBottomBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController      = navController,
            startDestination   = Screen.SpeedTest.route,
            modifier           = Modifier.padding(innerPadding),
            enterTransition    = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) },
            exitTransition     = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 3 } },
            popExitTransition  = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 3 } }
        ) {
            composable(Screen.SpeedTest.route)  { SpeedTestScreen(vm = speedTestVm) }
            composable(Screen.WifiScan.route)   { WifiScanScreen(vm = wifiVm) }
            composable(Screen.Signal.route)     { SignalQualityScreen(vm = wifiVm) }
            composable(Screen.Direction.route)  { DirectionFinderScreen(vm = directionVm) }
        }
    }
}

// ─── Bottom Navigation Bar ────────────────────────────────────────────────────
@Composable
fun NetScannerBottomBar(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, DarkNavy.copy(alpha = 0.98f))
                )
            )
    ) {
        // Top separator glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, CyanPrimary.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            navScreens.forEach { screen ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                NavBarItem(
                    screen     = screen,
                    isSelected = isSelected,
                    onClick    = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    screen:     Screen,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue   = if (isSelected) 1.0f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "navScale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label         = "navAlpha"
    )

    // Accent color differs per tab
    val accentColor = when (screen) {
        Screen.SpeedTest -> CyanPrimary
        Screen.WifiScan  -> NeonGreen
        Screen.Signal    -> NeonPurple
        Screen.Direction -> NeonOrange
        else             -> CyanPrimary
    }

    Column(
        modifier = Modifier
            .scale(animatedScale)
            .clickable(
                indication        = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick           = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = accentColor.copy(alpha = 0.15f * indicatorAlpha),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    accentColor.copy(alpha = 0.4f * indicatorAlpha),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isSelected) screen.iconSel else screen.icon,
                contentDescription = screen.label,
                tint               = if (isSelected) accentColor else TextMuted,
                modifier           = Modifier.size(22.dp)
            )
        }
        Text(
            text       = screen.label,
            fontSize   = 10.sp,
            color      = if (isSelected) accentColor else TextMuted,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines   = 1
        )
    }
}
