package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var repo: SettingsRepository
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeEach
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "settings.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
    }

    @Test
    fun `default language is system`() = runTest {
        repo.languageFlow.test {
            assertEquals(SettingsRepository.Language.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage persists and emits new value`() = runTest {
        repo.setLanguage(SettingsRepository.Language.FR)
        repo.languageFlow.test {
            assertEquals(SettingsRepository.Language.FR, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default briefing time is 8h00`() = runTest {
        repo.dailyBriefingHourFlow.test {
            assertEquals(8, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDailyBriefingHour persists`() = runTest {
        repo.setDailyBriefingHour(7)
        repo.dailyBriefingHourFlow.test {
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default selected llm provider is ollama`() = runTest {
        repo.selectedLlmProviderFlow.test {
            assertEquals(SettingsRepository.LlmProvider.OLLAMA, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSelectedLlmProvider persists ollama and other values`() = runTest {
        repo.setSelectedLlmProvider(SettingsRepository.LlmProvider.CLAUDE)
        repo.selectedLlmProviderFlow.test {
            assertEquals(SettingsRepository.LlmProvider.CLAUDE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repo.setSelectedLlmProvider(SettingsRepository.LlmProvider.OLLAMA)
        repo.selectedLlmProviderFlow.test {
            assertEquals(SettingsRepository.LlmProvider.OLLAMA, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
