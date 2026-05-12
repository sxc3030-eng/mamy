package com.mamy.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.mamy.android.R
import com.mamy.android.data.wakeword.WakeWordEngine
import com.mamy.android.data.wakeword.WakeWordSensitivity
import com.mamy.android.domain.capture.CaptureEvent
import com.mamy.android.domain.capture.CapturePipeline
import com.mamy.android.domain.capture.StructuredCapturePipeline
import com.mamy.android.util.Lang
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MamYListenerService : Service() {

    @Inject lateinit var wakeWord: WakeWordEngine
    @Inject lateinit var pipeline: CapturePipeline
    @Inject lateinit var structurer: StructuredCapturePipeline

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val captureMutex = Mutex()
    @Volatile private var captureJob: Job? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CaptureNotification.ensureChannel(this)
        val notif = CaptureNotification.build(this, CaptureEvent.Idle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                CaptureNotification.NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(CaptureNotification.NOTIF_ID, notif)
        }
        startWakeWord()
        observeEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_CAPTURE) {
            triggerCaptureNow()
        }
        return START_STICKY
    }

    private fun startWakeWord() {
        try {
            wakeWord.start(WakeWordSensitivity.DEFAULT) {
                triggerCaptureNow()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wake-word start failed", t)
        }
    }

    private fun triggerCaptureNow() {
        val existing = captureJob
        if (existing?.isActive == true) {
            Log.w(TAG, "trigger ignored — capture already running")
            return
        }
        captureJob = scope.launch {
            captureMutex.withLock {
                // Pause wake-word during capture (mic conflict)
                wakeWord.stop()
                try {
                    val lang = if (Locale.getDefault().language == "fr") "fr" else "en"
                    pipeline.runOneCapture(lang)
                } finally {
                    // Resume wake-word
                    try {
                        wakeWord.start(WakeWordSensitivity.DEFAULT) { triggerCaptureNow() }
                    } catch (t: Throwable) {
                        Log.e(TAG, "wake-word restart failed", t)
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            pipeline.events.collectLatest { ev ->
                Log.i(TAG, "event=$ev")
                val notif = CaptureNotification.build(this@MamYListenerService, ev)
                startForegroundCompat(notif)
                when (ev) {
                    is CaptureEvent.NoSpeech -> showToast(getString(R.string.capture_toast_no_speech))
                    is CaptureEvent.MaxDurationHit -> showToast(getString(R.string.capture_toast_max_duration))
                    is CaptureEvent.Error -> {
                        Log.e(TAG, "capture error", ev.cause)
                        showToast(getString(R.string.capture_toast_error))
                    }
                    is CaptureEvent.TranscriptReady -> {
                        Log.i(TAG, "TRANSCRIPT: ${ev.text} (intent=${ev.intent}, dur=${ev.durationSec}s)")
                        val preview = ev.text.take(60).let { if (ev.text.length > 60) "$it…" else it }
                        showToast(getString(R.string.capture_toast_saved, preview))
                        val lang = if (Locale.getDefault().language == "fr") Lang.FR else Lang.EN
                        runCatching { structurer.handle(ev.text, lang, ev.durationSec) }
                            .onFailure {
                                Log.e(TAG, "structurer.handle failed", it)
                                showToast(getString(R.string.capture_toast_structure_failed))
                            }
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Toasts must run on the main looper; the capture pipeline lives on Dispatchers.Default. */
    private fun showToast(text: String) {
        mainHandler.post {
            Toast.makeText(this@MamYListenerService, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun startForegroundCompat(notif: android.app.Notification) {
        val mgr = getSystemService(android.app.NotificationManager::class.java)
        mgr?.notify(CaptureNotification.NOTIF_ID, notif)
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        try { wakeWord.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MamYListenerService"
        const val ACTION_TRIGGER_CAPTURE = "com.mamy.android.action.TRIGGER_CAPTURE"
    }
}
