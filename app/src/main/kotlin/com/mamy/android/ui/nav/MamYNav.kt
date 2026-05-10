package com.mamy.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mamy.android.ui.screens.actions.ActionsRoute
import com.mamy.android.ui.screens.calendar.CalendarRoute
import com.mamy.android.ui.screens.data.DataRoute
import com.mamy.android.ui.screens.notes.NotesRoute
import com.mamy.android.ui.screens.networklog.NetworkLogRoute
import com.mamy.android.ui.screens.onboarding.OnboardingRoute
import com.mamy.android.ui.screens.person.PersonDetailRoute
import com.mamy.android.ui.screens.person.PersonDetailViewModel
import com.mamy.android.ui.screens.reports.ReportsListRoute
import com.mamy.android.ui.screens.settings.SettingsRoute
import com.mamy.android.ui.screens.sms.SmsHistoryRoute

@Composable
fun MamYNav(navController: NavHostController) {
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
            arguments = listOf(
                navArgument(PersonDetailViewModel.ARG_PERSON_ID) {
                    type = NavType.StringType
                }
            ),
        ) {
            PersonDetailRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.Actions.path) {
            ActionsRoute()
        }
        composable(Routes.Calendar.path) {
            CalendarRoute()
        }
        composable(Routes.Notes.path) {
            NotesRoute()
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
