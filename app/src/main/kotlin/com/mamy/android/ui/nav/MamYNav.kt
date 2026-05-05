package com.mamy.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mamy.android.ui.screens.ActionsScreen
import com.mamy.android.ui.screens.NetworkLogScreen
import com.mamy.android.ui.screens.OnboardingScreen
import com.mamy.android.ui.screens.PersonDetailScreen
import com.mamy.android.ui.screens.ReportsListScreen
import com.mamy.android.ui.screens.SettingsScreen

@Composable
fun MamYNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ReportsList.route,
    ) {
        composable(Routes.Onboarding.route) {
            OnboardingScreen()
        }
        composable(Routes.ReportsList.route) {
            ReportsListScreen(onPersonClick = { personId ->
                navController.navigate(Routes.PersonDetail.path(personId))
            })
        }
        composable(
            route = Routes.PersonDetail.route,
            arguments = listOf(navArgument(Routes.PersonDetail.ARG_PERSON_ID) { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString(Routes.PersonDetail.ARG_PERSON_ID).orEmpty()
            PersonDetailScreen(personId = id)
        }
        composable(Routes.Actions.route) {
            ActionsScreen()
        }
        composable(Routes.Settings.route) {
            SettingsScreen(onNetworkLogClick = {
                navController.navigate(Routes.NetworkLog.route)
            })
        }
        composable(Routes.NetworkLog.route) {
            NetworkLogScreen()
        }
    }
}
