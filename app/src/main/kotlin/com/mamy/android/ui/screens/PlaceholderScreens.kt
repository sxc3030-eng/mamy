package com.mamy.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mamy.android.R

@Composable
fun OnboardingScreen() = Centered(stringResource(R.string.screen_onboarding_title))

@Composable
fun ReportsListScreen(onPersonClick: (String) -> Unit) =
    Centered(stringResource(R.string.screen_reports_title))

// PersonDetailScreen + ActionsScreen are now provided by their dedicated
// modules in `ui/screens/person/` (PersonDetailRoute) and `ui/screens/actions/`
// (ActionsRoute). MamYNav wires those routes directly via @HiltViewModel.

@Composable
fun SettingsScreen(onNetworkLogClick: () -> Unit) =
    Centered(stringResource(R.string.screen_settings_title))

@Composable
fun NetworkLogScreen() = Centered(stringResource(R.string.screen_network_log_title))

@Composable
private fun Centered(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}
