package com.mamy.android.ui.common

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/** Compact mic icon-button for trailing slot inside an OutlinedTextField. */
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
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(contentDescriptionRes),
        )
    }
}

/**
 * Voice-first FAB — tap to dictate, the recognized string is delivered
 * synchronously to [onResult]. Designed to sit above the secondary "Add"
 * FAB on screens whose primary entry is voice (Notes, Actions, …).
 */
@Composable
fun VoiceFab(
    onResult: (String) -> Unit,
    contentDescription: String,
    testTagName: String,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberSpeechToTextLauncher(onResult)
    FloatingActionButton(
        onClick = { launcher.launch() },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.testTag(testTagName),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = contentDescription,
        )
    }
}
