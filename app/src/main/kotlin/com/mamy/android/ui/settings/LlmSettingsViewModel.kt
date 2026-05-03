package com.mamy.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface KeyTestResult {
    object Idle : KeyTestResult
    data class Pending(val providerId: String) : KeyTestResult
    data class Success(val providerId: String) : KeyTestResult
    data class Failure(val providerId: String, val message: String) : KeyTestResult
}

@HiltViewModel
class LlmSettingsViewModel @Inject constructor(
    private val factory: LlmProviderFactory,
    private val vault: SecretsVault,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _result = MutableStateFlow<KeyTestResult>(KeyTestResult.Idle)
    val lastTestResult: StateFlow<KeyTestResult> = _result.asStateFlow()

    fun availableProviders(): List<LlmProvider> = factory.all()

    fun saveKey(providerId: String, key: String) {
        viewModelScope.launch { vault.setKey(providerId, key) }
    }

    fun testKey(providerId: String) {
        viewModelScope.launch {
            _result.value = KeyTestResult.Pending(providerId)
            val provider = factory.byId(providerId)
            _result.value = provider.testKey().fold(
                onSuccess = { KeyTestResult.Success(providerId) },
                onFailure = { KeyTestResult.Failure(providerId, it.message ?: "Unknown error") },
            )
        }
    }

    fun selectProvider(providerId: String) {
        val mapped = when (providerId) {
            LlmProviderId.CLAUDE -> SettingsRepository.LlmProvider.CLAUDE
            LlmProviderId.OPENAI -> SettingsRepository.LlmProvider.OPENAI
            LlmProviderId.GEMINI -> SettingsRepository.LlmProvider.GEMINI
            else -> SettingsRepository.LlmProvider.CLAUDE
        }
        viewModelScope.launch { settings.setSelectedLlmProvider(mapped) }
    }
}
