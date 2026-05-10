package com.mamy.android.ui.common

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mamy.android.R

/**
 * Encapsulated launcher for the system Speech-to-Text dialog
 * ([RecognizerIntent.ACTION_RECOGNIZE_SPEECH]). Most Android phones with
 * Gboard / Samsung Voice / similar already expose this engine — no extra
 * dependency, works with the device's preferred recognizer (offline on
 * Pixel/Samsung recent, online otherwise).
 *
 * Usage from a Composable:
 * ```kotlin
 * val sttLauncher = rememberSpeechToTextLauncher { recognized ->
 *     myField = (myField + " " + recognized).trim()
 * }
 * MicButton(onClick = { sttLauncher.launch() })
 * ```
 */
@Composable
fun rememberSpeechToTextLauncher(
    onResult: (String) -> Unit,
): SpeechToTextLauncher {
    val locale = LocalConfiguration.current.locales[0]?.toLanguageTag() ?: "en-US"
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val results = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onResult(it) }
        }
    }
    return remember(activityLauncher, locale) {
        SpeechToTextLauncher {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Mamy")
            }
            try {
                activityLauncher.launch(intent)
            } catch (_: Throwable) {
                // No STT engine on the device — silently no-op. UI hint elsewhere
                // could surface "Install Google app for voice typing" if needed.
            }
        }
    }
}

class SpeechToTextLauncher internal constructor(private val launch: () -> Unit) {
    fun launch() = launch.invoke()
}

/**
 * Standard "🎤" button to attach to a TextField row. Uses the `Email` icon as
 * a stand-in mic icon (material-icons-core has no Mic, and we don't ship
 * material-icons-extended yet). Replace with a vector drawable in V1.0.
 */
@Composable
fun MicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionRes: Int = R.string.mic_button_cd,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("mic-button"),
    ) {
        Icon(
            imageVector = Icons.Filled.Email, // TODO V1.0 swap to Mic vector drawable
            contentDescription = stringResource(contentDescriptionRes),
        )
    }
}
