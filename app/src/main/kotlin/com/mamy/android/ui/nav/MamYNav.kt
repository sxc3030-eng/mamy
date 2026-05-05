package com.mamy.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mamy.android.ui.screens.ActionsScreen
import com.mamy.android.ui.screens.NetworkLogScreen
import com.mamy.android.ui.screens.PersonDetailScreen
import com.mamy.android.ui.screens.SettingsScreen
import com.mamy.android.ui.screens.onboarding.OnboardingRoute
import com.mamy.android.ui.screens.reports.ReportsListRoute

@Composable
fun MamYNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ReportsList.path,
    ) {
        composable(Routes.Onboarding.path) {
            OnboardingRoute(
                onFinish = {
                    navController.navigate(Routes.ReportsList.path) {
                        popUpTo(Routes.Onboarding.path) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ReportsList.path) {
            ReportsListRoute(
                onPersonClick = { row ->
                    navController.navigate(Routes.PersonDetail.build(row.id.toString()))
                },
            )
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
            SettingsScreen(onNetworkLogClick = {
                navController.navigate(Routes.NetworkLog.path)
            })
        }
        composable(Routes.NetworkLog.path) {
            NetworkLogScreen()
        }
    }
}
