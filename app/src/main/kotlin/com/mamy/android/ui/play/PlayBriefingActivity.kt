package com.mamy.android.ui.play

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * Deep-link target for briefing notifications.
 *
 * URI shape: `mamy://play/{type}?targetId={id}`
 *   - type = "daily" | "pre_meeting"
 *   - targetId optional (set for pre_meeting only)
 *
 * V1 P6 ships only the shell. Real "play" UI (waveform, transcript) is P7.
 * This activity reads intent extras, kicks off TTS playback via DI'd
 * TtsService, then closes itself when speech ends.
 */
@AndroidEntryPoint
class PlayBriefingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent?.data?.lastPathSegment ?: "daily"
        val targetId = intent?.data?.getQueryParameter("targetId")
        setContent { PlayingScreen(label = "Briefing: $type ${targetId.orEmpty()}") }
        // Hand off to handler — wired in BriefingModule (Task 22 scope: smoke test).
    }
}

@Composable
private fun PlayingScreen(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
