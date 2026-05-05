package com.mamy.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mamy.android.ui.screens.ActionsScreen
import com.mamy.android.ui.screens.OnboardingScreen
import com.mamy.android.ui.screens.PersonDetailScreen
import com.mamy.android.ui.screens.ReportsListScreen
import com.mamy.android.ui.screens.data.DataRoute
import com.mamy.android.ui.screens.networklog.NetworkLogRoute
import com.mamy.android.ui.screens.settings.SettingsRoute
import com.mamy.android.ui.screens.sms.SmsHistoryRoute

@Composable
fun MamYNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ReportsList.path,
    ) {
        composable(Routes.Onboarding.path) {
            OnboardingScreen()
        }
        composable(Routes.ReportsList.path) {
            ReportsListScreen(onPersonClick = { personId ->
                navController.navigate(Routes.PersonDetail.build(personId))
            })
        }
        composable(
            route = Routes.PersonDetail.path,
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("personId").orEmpty()
            PersonDetailScreen(personId = id)
        }
        composable(Routes.Actions.path) {
            ActionsScreen()
        }
        composable(Routes.Settings.path) {
            SettingsRoute(
                onOpenNetworkLog = { navController.navigate(Routes.NetworkLog.path) },
                onOpenData = { navController.navigate(Routes.Data.path) },
                onOpenSmsAuditLog = { navController.navigate(Routes.NetworkLog.path) },
            )
        }
        composable(Routes.NetworkLog.path) {
            NetworkLogRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.Data.path) {
            DataRoute(
                onOpenSmsHistory = { navController.navigate(Routes.SmsHistory.path) },
            )
        }
        composable(Routes.SmsHistory.path) {
            SmsHistoryRoute()
        }
    }
}
