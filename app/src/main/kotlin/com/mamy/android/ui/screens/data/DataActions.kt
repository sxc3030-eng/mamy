package com.mamy.android.ui.screens.data

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Side-effecting actions triggered from DataScreen buttons.
 * P8 will land the real export (JSONL + gzip + AES) and wipe (Room nukes + Vault clear);
 * for now the [NoOpDataActions] simulates them so the UI layer is fully testable.
 */
sealed interface ExportOutcome {
    data class Success(val path: String) : ExportOutcome
    data class Failure(val reason: String) : ExportOutcome
}

interface DataActions {
    /** Trigger an encrypted export of the user database. P8 wires the real impl. */
    suspend fun exportAll(passphrase: String): ExportOutcome

    /** Wipe everything (Room + DataStore + SecretsVault). Double-confirm at UI layer. */
    suspend fun wipeAll()

    /** Wipe a single person + their notes/actions/promises/SMS. */
    suspend fun wipePerson(personId: String)
}

@Singleton
class NoOpDataActions @Inject constructor() : DataActions {
    override suspend fun exportAll(passphrase: String): ExportOutcome {
        // Simulate a slow path so the UI's isExporting state is observable.
        delay(50)
        return ExportOutcome.Failure("Export not yet implemented (P8 incoming).")
    }

    override suspend fun wipeAll() { /* no-op until P8 wires real impl */ }
    override suspend fun wipePerson(personId: String) { /* no-op until P8 wires real impl */ }
}
