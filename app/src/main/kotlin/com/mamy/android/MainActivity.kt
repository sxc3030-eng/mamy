package com.mamy.android

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mamy.android.service.MamYListenerService
import com.mamy.android.ui.nav.MamYNav
import com.mamy.android.ui.theme.MamYTheme
import com.mamy.android.util.VolumeLongPressDetector
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val volDetector = VolumeLongPressDetector(onLongPress = ::triggerCapture)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MamYApp() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volDetector.onKeyDown(keyCode)) return true
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volDetector.onKeyUp(keyCode)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun triggerCapture() {
        val intent = Intent(this, MamYListenerService::class.java).apply {
            action = MamYListenerService.ACTION_TRIGGER_CAPTURE
        }
        startForegroundService(intent)
    }
}

@Composable
fun MamYApp() {
    MamYTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                MamYNav()
            }
        }
    }
}
