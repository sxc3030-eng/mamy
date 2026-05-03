package com.mamy.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.domain.calendar.CalendarOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarSettingsUiState(
    val calendarEnabled: Boolean = false,
    val syncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val onboarding: CalendarOnboardingUseCase,
    private val authManager: CalendarAuthManager,
    private val settings: CalendarSettings
) : ViewModel() {

    val uiState: StateFlow<CalendarSettingsUiState> =
        settings.isCalendarEnabled
            .map { CalendarSettingsUiState(calendarEnabled = it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSettingsUiState())

    fun onAuthCodeReceived(authCode: String, accountEmail: String, scope: String) {
        viewModelScope.launch {
            val result = onboarding.completeOnboarding(authCode, accountEmail, scope)
            // Error surfacing: map result to a side-channel SharedFlow in V1.1; for V1, log.
            result.exceptionOrNull()?.printStackTrace()
        }
    }

    fun onDisconnect() {
        viewModelScope.launch { onboarding.disconnect() }
    }
}
