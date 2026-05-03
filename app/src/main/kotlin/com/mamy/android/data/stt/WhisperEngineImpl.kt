package com.mamy.android.data.stt

import android.content.Context
import android.util.Log
import com.mamy.android.data.stt.jni.WhisperJni
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: WhisperModelDownloader,
    private val jni: WhisperJni,
    private val model: WhisperModel = WhisperModel.TINY,
) : WhisperEngine {

    private val initMutex = Mutex()
    @Volatile private var initialized: Boolean = false

    override suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        val f = File(modelsDir(), model.fileName)
        f.exists() && f.length() == model.expectedBytes
    }

    override fun downloadModel(): Flow<Int> = downloader.download(model)

    override suspend fun transcribe(pcm: ShortArray, language: String): Result<String> {
        return runCatching {
            ensureInit()
            withContext(Dispatchers.Default) {
                jni.transcribe(pcm, language).trim()
            }
        }.onFailure { Log.e(TAG, "transcribe failed", it) }
    }

    private suspend fun ensureInit() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            check(isModelReady()) { "Model not ready — call downloadModel() first" }
            val modelPath = File(modelsDir(), model.fileName).absolutePath
            withContext(Dispatchers.IO) { jni.initContext(modelPath) }
            initialized = true
        }
    }

    private fun modelsDir(): File = File(context.filesDir, "whisper").also { it.mkdirs() }

    private companion object { const val TAG = "WhisperEngine" }
}
