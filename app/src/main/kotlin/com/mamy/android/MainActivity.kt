package com.mamy.android

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mamy.android.service.MamYListenerService
import com.mamy.android.ui.nav.MamYNav
import com.mamy.android.ui.nav.Routes
import com.mamy.android.ui.theme.MamYTheme
import com.mamy.android.util.PermissionLauncher
import com.mamy.android.util.VolumeLongPressDetector
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val volDetector = VolumeLongPressDetector(onLongPress = ::triggerCapture)

    private val permLauncher = PermissionLauncher.register(this) { granted ->
        if (granted) startService(Intent(this, MamYListenerService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MamYApp() }
        val missing = PermissionLauncher.missing(this)
        if (missing.isEmpty()) {
            startService(Intent(this, MamYListenerService::class.java))
        } else {
            permLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volDetector.onKeyDown(keyCode)) return true
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volDetector.onKeyUp(keyCode)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun triggerCapture() {
        val intent = Intent(this, MamYListenerService::class.java).apply {
            action = MamYListenerService.ACTION_TRIGGER_CAPTURE
        }
        startForegroundService(intent)
    }
}

@Composable
fun MamYApp() {
    MamYTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val topLevelRoutes = setOf(
            Routes.ReportsList.path,
            Routes.Actions.path,
            Routes.Calendar.path,
            Routes.Notes.path,
            Routes.Settings.path,
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentRoute in topLevelRoutes) {
                    NavigationBar {
                        bottomNavItems().forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                MamYNav(navController = navController)
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @androidx.annotation.StringRes val labelRes: Int,
)

@Composable
private fun bottomNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem(Routes.ReportsList.path, Icons.AutoMirrored.Filled.List, R.string.nav_reports),
    BottomNavItem(Routes.Actions.path, Icons.Filled.CheckCircle, R.string.nav_actions),
    BottomNavItem(Routes.Calendar.path, Icons.Filled.DateRange, R.string.nav_calendar),
    BottomNavItem(Routes.Notes.path, Icons.Filled.Create, R.string.nav_notes),
    BottomNavItem(Routes.Settings.path, Icons.Filled.Settings, R.string.nav_settings),
)
