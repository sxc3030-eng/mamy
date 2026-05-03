# MamY P2 — Voice Capture Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the always-on voice capture pipeline : wake-word "MamY" → audio recording with VAD → local Whisper transcription, exposed via Flow from the foreground service. Output : a working pipeline where saying "MamY, test 1 2 3" produces a text transcript in a debug screen.

**Architecture:** Foreground service hosts Picovoice Porcupine wake-word engine (~3-5% CPU continu), on wake-word fire activates AudioRecord capture with WebRTC VAD silence detection (1.5s cut, 90s max), pipes PCM audio to whisper.cpp via JNI for local transcription, emits transcripts via Kotlin Flow to consumers.

**Tech Stack:** Picovoice Porcupine Android SDK 3.x · whisper.cpp (NDK build, JNI bindings) · WebRTC VAD · AudioRecord (Android native) · Coroutines Flow · Kotlin 2.0.21
---

## Pre-flight assumptions (from P1)

P2 assumes P1 has already shipped :
- `MamYApplication` (Hilt entry point) and Hilt setup
- `MamYListenerService.kt` skeleton : foreground service registered in manifest with `foregroundServiceType="microphone"`, basic `startForeground()` notification, `onStartCommand` returning `START_STICKY`. No pipeline yet — this plan wires it up.
- `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `INTERNET` declared in `AndroidManifest.xml`
- `data/secrets/SecretsRepository.kt` skeleton (BYOK keys) — used in P2 to read Picovoice access key
- libs.versions.toml with Kotlin 2.0.21, AGP 8.7.2, Compose BOM 2024.12.01, Hilt 2.52, JUnit Jupiter 5.11.3, MockK 1.13.13, Robolectric 4.14.1, Turbine 1.2.0, coroutines 1.9.0
- `app/src/main/assets/` directory exists
- Branch from P1 finished : `main` is up-to-date. Create branch `p2-voice-capture` for this plan.

If any of those are missing the plan still produces working code but you'll need to wire the imports/manifest entries first.

## Conventions reminders
- Path absolu Windows partout (`D:/ComfyUI-Intel/mamy/...`)
- Tests unit : `app/src/test/kotlin/com/mamy/android/...` (JUnit 5 Jupiter + MockK + Robolectric)
- Tests instrumented : `app/src/androidTest/kotlin/com/mamy/android/...`
- Pas de string hardcodé après P1 : ajouter à `res/values/strings.xml` + `res/values-fr/strings.xml`
- Commit prefix : `feat:` / `fix:` / `test:` / `refactor:` / `chore:` avec footer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`

---

## Task 1 — Add Porcupine Android SDK + assets dir

**Files:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/assets/wakeword/.gitkeep` (placeholder so dir is tracked)
- `app/src/main/assets/wakeword/README.md` (instructions for adding `.ppn` files)

### Steps

- [ ] **Step 1.1 — Add porcupine version to versions catalog**

  Edit `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml` and add to `[versions]` section :

  ```toml
  porcupine = "3.0.2"
  ```

  And to `[libraries]` :

  ```toml
  porcupine-android = { module = "ai.picovoice:porcupine-android", version.ref = "porcupine" }
  ```

- [ ] **Step 1.2 — Wire into app/build.gradle.kts**

  Open `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` and add inside `dependencies { ... }` :

  ```kotlin
  implementation(libs.porcupine.android)
  ```

  Inside the existing `android { ... }` block, add (or merge into existing `aaptOptions`/`androidResources`) :

  ```kotlin
  androidResources {
      noCompress += listOf("ppn", "pv", "bin")
  }
  ```

  (Porcupine model files are pre-compressed and must not be re-zipped by aapt.)

- [ ] **Step 1.3 — Create assets dir for wake-word models**

  Create directory `D:/ComfyUI-Intel/mamy/app/src/main/assets/wakeword/` and add a `.gitkeep` file (empty) to track it.

  Create `D:/ComfyUI-Intel/mamy/app/src/main/assets/wakeword/README.md` :

  ```markdown
  # Wake-word models

  Place Picovoice Porcupine `.ppn` model files here :

  - `mamy_en.ppn` — English wake-word "MamY", trained via Picovoice Console
  - `mamy_fr.ppn` — French wake-word "MamY"

  Both files are loaded at runtime by `PorcupineEngine` based on the user's UI language.
  Models are typically 100-150 KB each.

  Picovoice Console : https://console.picovoice.ai/
  ```

  Note : actual `.ppn` files are NOT committed (they ship via app bundle, not source). For dev they must be dropped here manually before running the app.

- [ ] **Step 1.4 — Sanity check Gradle sync**

  Run from `D:/ComfyUI-Intel/mamy/` :

  ```
  ./gradlew :app:dependencies --configuration debugCompileClasspath | grep porcupine
  ```

  Expected : a line containing `ai.picovoice:porcupine-android:3.0.2`. If absent, the sync didn't pick up the dep — re-check libs.versions.toml syntax.

- [ ] **Step 1.5 — Commit**

  ```
  git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/assets/wakeword/
  git commit -m "chore: add Picovoice Porcupine SDK + wakeword assets dir"
  ```

---

## Task 2 — `WakeWordEngine` class (init, sensitivity, listener)

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordSensitivity.kt`
- `app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordListener.kt`
- `app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordEngine.kt`
- `app/src/main/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngine.kt`
- `app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordModelResolver.kt`

### Steps

- [ ] **Step 2.1 — Sensitivity enum**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordSensitivity.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  /**
   * User-tunable wake-word sensitivity.
   * Maps to Picovoice Porcupine sensitivity float (0.0 strict … 1.0 permissive).
   */
  enum class WakeWordSensitivity(val porcupineFloat: Float) {
      LOW(0.35f),
      MEDIUM(0.55f),
      HIGH(0.75f);

      companion object {
          val DEFAULT = MEDIUM
      }
  }
  ```

- [ ] **Step 2.2 — Listener + Engine interface**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordListener.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  fun interface WakeWordListener {
      /** Called on background audio thread when "MamY" is detected. */
      fun onWakeWordDetected()
  }
  ```

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordEngine.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  /**
   * Continuous wake-word listener. Implementations own a background audio thread.
   * Lifecycle : [start] → idle on a callback thread → [stop] → [release].
   */
  interface WakeWordEngine {
      fun start(sensitivity: WakeWordSensitivity, listener: WakeWordListener)
      fun stop()
      fun release()
      fun isRunning(): Boolean
  }
  ```

- [ ] **Step 2.3 — Model resolver (locale → asset path)**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/WakeWordModelResolver.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  import android.content.Context
  import java.io.File
  import java.io.FileOutputStream
  import java.util.Locale

  /**
   * Copies the appropriate `.ppn` asset to internal storage (Porcupine needs a real path)
   * and returns the absolute path.
   */
  class WakeWordModelResolver(private val context: Context) {

      fun resolveKeywordPath(locale: Locale): String {
          val assetName = when (locale.language.lowercase()) {
              "fr" -> "wakeword/mamy_fr.ppn"
              else -> "wakeword/mamy_en.ppn"
          }
          val outFile = File(context.filesDir, assetName.substringAfterLast('/'))
          if (!outFile.exists() || outFile.length() == 0L) {
              context.assets.open(assetName).use { input ->
                  FileOutputStream(outFile).use { output -> input.copyTo(output) }
              }
          }
          return outFile.absolutePath
      }
  }
  ```

  Note : Porcupine SDK API takes a file path or asset path; we copy to `filesDir` for a stable absolute path.

---

## Task 3 — `WakeWordEngine` Robolectric tests (interface contract)

**Files:**
- `app/src/test/kotlin/com/mamy/android/data/wakeword/WakeWordSensitivityTest.kt`
- `app/src/test/kotlin/com/mamy/android/data/wakeword/WakeWordModelResolverTest.kt`

### Steps

- [ ] **Step 3.1 — Failing test for sensitivity mapping**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/wakeword/WakeWordSensitivityTest.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Test

  class WakeWordSensitivityTest {

      @Test
      fun `LOW maps to 0_35`() {
          assertEquals(0.35f, WakeWordSensitivity.LOW.porcupineFloat, 0.0001f)
      }

      @Test
      fun `MEDIUM maps to 0_55 and is the default`() {
          assertEquals(0.55f, WakeWordSensitivity.MEDIUM.porcupineFloat, 0.0001f)
          assertEquals(WakeWordSensitivity.MEDIUM, WakeWordSensitivity.DEFAULT)
      }

      @Test
      fun `HIGH maps to 0_75`() {
          assertEquals(0.75f, WakeWordSensitivity.HIGH.porcupineFloat, 0.0001f)
      }
  }
  ```

- [ ] **Step 3.2 — Failing test for model resolver (Robolectric)**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/wakeword/WakeWordModelResolverTest.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import org.junit.Assert.assertTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import java.io.File
  import java.util.Locale

  @RunWith(AndroidJUnit4::class)
  class WakeWordModelResolverTest {

      @Test
      fun `resolves english model to filesDir absolute path`() {
          // Robolectric requires a fake asset; we write one before the call
          val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
          // shadowOf the assets manager : drop a fake file
          File(ctx.filesDir, "mamy_en.ppn").writeBytes(ByteArray(16) { it.toByte() })

          val resolver = WakeWordModelResolver(ctx)
          val path = resolver.resolveKeywordPath(Locale.ENGLISH)

          assertTrue("path should be absolute", File(path).isAbsolute)
          assertTrue("file must exist", File(path).exists())
          assertTrue("path under filesDir", path.startsWith(ctx.filesDir.absolutePath))
      }

      @Test
      fun `resolves french model when locale=fr`() {
          val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
          File(ctx.filesDir, "mamy_fr.ppn").writeBytes(ByteArray(16) { it.toByte() })

          val resolver = WakeWordModelResolver(ctx)
          val path = resolver.resolveKeywordPath(Locale.FRENCH)

          assertTrue(path.endsWith("mamy_fr.ppn"))
      }
  }
  ```

  Note : Robolectric AssetManager doesn't see assets injected at runtime, so the resolver test side-steps `assets.open()` by pre-writing the file to `filesDir` (the resolver's "exists?" check short-circuits the copy).

- [ ] **Step 3.3 — Run tests, expect FAIL on first run if compile errors, PASS once classes exist**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.wakeword.*"
  ```

  Expected after Task 2 implementation : 5 tests, all PASS. If any fail, fix before continuing.

- [ ] **Step 3.4 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/wakeword/ app/src/test/kotlin/com/mamy/android/data/wakeword/
  git commit -m "feat: WakeWordEngine interface + sensitivity + model resolver"
  ```

---

## Task 4 — Porcupine implementation of `WakeWordEngine`

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngine.kt`

This is the concrete Porcupine-backed implementation. It can't be unit-tested without the SDK being mocked or a real `.ppn` file, so we cover it via instrumented test in Task 5.

### Steps

- [ ] **Step 4.1 — Implementation**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngine.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  import ai.picovoice.porcupine.PorcupineManager
  import ai.picovoice.porcupine.PorcupineManagerCallback
  import android.content.Context
  import android.util.Log
  import java.util.Locale
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class PorcupineWakeWordEngine @Inject constructor(
      private val context: Context,
      private val resolver: WakeWordModelResolver,
      private val accessKeyProvider: () -> String,
      private val localeProvider: () -> Locale,
  ) : WakeWordEngine {

      @Volatile private var manager: PorcupineManager? = null
      @Volatile private var running: Boolean = false

      override fun start(sensitivity: WakeWordSensitivity, listener: WakeWordListener) {
          if (running) {
              Log.w(TAG, "start() called while already running — ignoring")
              return
          }
          val accessKey = accessKeyProvider().also {
              require(it.isNotBlank()) { "Picovoice access key missing" }
          }
          val keywordPath = resolver.resolveKeywordPath(localeProvider())
          val callback = PorcupineManagerCallback { _ -> listener.onWakeWordDetected() }

          manager = PorcupineManager.Builder()
              .setAccessKey(accessKey)
              .setKeywordPath(keywordPath)
              .setSensitivity(sensitivity.porcupineFloat)
              .build(context, callback)
              .also { it.start() }
          running = true
          Log.i(TAG, "Porcupine started, keyword=$keywordPath sens=${sensitivity.porcupineFloat}")
      }

      override fun stop() {
          if (!running) return
          try {
              manager?.stop()
          } catch (t: Throwable) {
              Log.w(TAG, "stop() failed", t)
          }
          running = false
          Log.i(TAG, "Porcupine stopped")
      }

      override fun release() {
          stop()
          try {
              manager?.delete()
          } catch (t: Throwable) {
              Log.w(TAG, "release() failed", t)
          }
          manager = null
      }

      override fun isRunning(): Boolean = running

      private companion object { const val TAG = "PorcupineEngine" }
  }
  ```

- [ ] **Step 4.2 — Hilt module wiring**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/WakeWordModule.kt` :

  ```kotlin
  package com.mamy.android.di

  import android.content.Context
  import com.mamy.android.data.secrets.SecretsRepository
  import com.mamy.android.data.wakeword.PorcupineWakeWordEngine
  import com.mamy.android.data.wakeword.WakeWordEngine
  import com.mamy.android.data.wakeword.WakeWordModelResolver
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.android.qualifiers.ApplicationContext
  import dagger.hilt.components.SingletonComponent
  import java.util.Locale
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  object WakeWordModule {

      @Provides @Singleton
      fun provideModelResolver(@ApplicationContext ctx: Context): WakeWordModelResolver =
          WakeWordModelResolver(ctx)

      @Provides @Singleton
      fun provideWakeWordEngine(
          @ApplicationContext ctx: Context,
          resolver: WakeWordModelResolver,
          secrets: SecretsRepository,
      ): WakeWordEngine = PorcupineWakeWordEngine(
          context = ctx,
          resolver = resolver,
          accessKeyProvider = { secrets.getPicovoiceAccessKey().orEmpty() },
          localeProvider = { Locale.getDefault() },
      )
  }
  ```

  Assumes `SecretsRepository.getPicovoiceAccessKey(): String?` exists from P1. If it doesn't, add a stub :

  ```kotlin
  // in P1's SecretsRepository.kt — add this method
  fun getPicovoiceAccessKey(): String? = prefs.getString(KEY_PICOVOICE, null)
  fun setPicovoiceAccessKey(key: String) { prefs.edit().putString(KEY_PICOVOICE, key).apply() }
  private companion object { const val KEY_PICOVOICE = "picovoice_access_key" }
  ```

- [ ] **Step 4.3 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

  Expected : BUILD SUCCESSFUL. If `SecretsRepository.getPicovoiceAccessKey` doesn't exist, the compile fails — wire the stub above.

- [ ] **Step 4.4 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngine.kt app/src/main/kotlin/com/mamy/android/di/WakeWordModule.kt app/src/main/kotlin/com/mamy/android/data/secrets/SecretsRepository.kt
  git commit -m "feat: PorcupineWakeWordEngine + Hilt wiring"
  ```

---

## Task 5 — Wake-word instrumented smoke test

**Files:**
- `app/src/androidTest/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngineInstrumentedTest.kt`

This test is gated behind a system property — it only runs when a real `.ppn` and access key are present, otherwise it is skipped. We don't fail CI when assets are missing.

### Steps

- [ ] **Step 5.1 — Test**

  Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/data/wakeword/PorcupineWakeWordEngineInstrumentedTest.kt` :

  ```kotlin
  package com.mamy.android.data.wakeword

  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Assume.assumeTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import java.util.Locale

  @RunWith(AndroidJUnit4::class)
  class PorcupineWakeWordEngineInstrumentedTest {

      @Test
      fun start_then_stop_lifecycle_does_not_crash() {
          val accessKey = System.getenv("PICOVOICE_ACCESS_KEY").orEmpty()
          assumeTrue(
              "Skipping : set env PICOVOICE_ACCESS_KEY and place mamy_en.ppn in assets to run",
              accessKey.isNotBlank(),
          )

          val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
          val resolver = WakeWordModelResolver(ctx)
          val engine = PorcupineWakeWordEngine(
              context = ctx,
              resolver = resolver,
              accessKeyProvider = { accessKey },
              localeProvider = { Locale.ENGLISH },
          )

          engine.start(WakeWordSensitivity.MEDIUM) { /* no-op listener */ }
          assertTrue(engine.isRunning())

          // Let it run briefly to confirm stable
          Thread.sleep(500)
          engine.stop()
          assertFalse(engine.isRunning())

          engine.release()
      }
  }
  ```

- [ ] **Step 5.2 — Document test gating**

  Append to `D:/ComfyUI-Intel/mamy/app/src/main/assets/wakeword/README.md` :

  ```markdown

  ## Running the instrumented smoke test

  Requires real Picovoice setup :

  1. Drop `mamy_en.ppn` (and optionally `mamy_fr.ppn`) in this directory.
  2. Set env var `PICOVOICE_ACCESS_KEY` to your Picovoice Console access key.
  3. Run : `./gradlew :app:connectedDebugAndroidTest --tests "*PorcupineWakeWordEngineInstrumentedTest*"`

  Without those, the test is auto-skipped (Assume.assumeTrue).
  ```

- [ ] **Step 5.3 — Commit**

  ```
  git add app/src/androidTest/kotlin/com/mamy/android/data/wakeword/ app/src/main/assets/wakeword/README.md
  git commit -m "test: wake-word lifecycle instrumented smoke (gated)"
  ```

---

## Task 6 — `AudioCapture` class (AudioRecord 16 kHz mono PCM lifecycle)

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/audio/AudioFormat.kt`
- `app/src/main/kotlin/com/mamy/android/data/audio/AudioCapture.kt`
- `app/src/main/kotlin/com/mamy/android/data/audio/AudioCaptureImpl.kt`

### Steps

- [ ] **Step 6.1 — Constants**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/AudioFormat.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  /** 16-bit mono PCM @ 16 kHz — the format Whisper consumes natively. */
  object AudioFormat {
      const val SAMPLE_RATE_HZ = 16_000
      const val FRAME_DURATION_MS = 30                  // 480 samples/frame, WebRTC VAD friendly
      const val SAMPLES_PER_FRAME = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000  // 480
      const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 16-bit = 2 bytes
      const val MAX_DURATION_SEC = 90
      const val MAX_SAMPLES = SAMPLE_RATE_HZ * MAX_DURATION_SEC
  }
  ```

- [ ] **Step 6.2 — Interface**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/AudioCapture.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import kotlinx.coroutines.flow.Flow

  /**
   * Streams 16-bit PCM frames from the mic. Each emitted [ShortArray] is exactly
   * [AudioFormat.SAMPLES_PER_FRAME] samples. Cold flow : starts AudioRecord on collection,
   * releases on cancellation.
   */
  interface AudioCapture {
      /** Throws SecurityException if RECORD_AUDIO permission missing. */
      fun frames(): Flow<ShortArray>
  }
  ```

- [ ] **Step 6.3 — Implementation**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/AudioCaptureImpl.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import android.Manifest
  import android.annotation.SuppressLint
  import android.content.Context
  import android.content.pm.PackageManager
  import android.media.AudioRecord
  import android.media.MediaRecorder
  import android.util.Log
  import androidx.core.content.ContextCompat
  import dagger.hilt.android.qualifiers.ApplicationContext
  import kotlinx.coroutines.channels.awaitClose
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.callbackFlow
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class AudioCaptureImpl @Inject constructor(
      @ApplicationContext private val context: Context,
  ) : AudioCapture {

      override fun frames(): Flow<ShortArray> = callbackFlow {
          if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
              != PackageManager.PERMISSION_GRANTED) {
              throw SecurityException("RECORD_AUDIO permission not granted")
          }

          val minBuf = AudioRecord.getMinBufferSize(
              AudioFormat.SAMPLE_RATE_HZ,
              android.media.AudioFormat.CHANNEL_IN_MONO,
              android.media.AudioFormat.ENCODING_PCM_16BIT,
          )
          val bufferSize = maxOf(minBuf, AudioFormat.BYTES_PER_FRAME * 4)

          @SuppressLint("MissingPermission")
          val record = AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              AudioFormat.SAMPLE_RATE_HZ,
              android.media.AudioFormat.CHANNEL_IN_MONO,
              android.media.AudioFormat.ENCODING_PCM_16BIT,
              bufferSize,
          )

          if (record.state != AudioRecord.STATE_INITIALIZED) {
              record.release()
              throw IllegalStateException("AudioRecord init failed (state=${record.state})")
          }

          val frame = ShortArray(AudioFormat.SAMPLES_PER_FRAME)
          @Volatile var stop = false

          val thread = Thread({
              try {
                  record.startRecording()
                  while (!stop && !Thread.currentThread().isInterrupted) {
                      var read = 0
                      while (read < frame.size) {
                          val n = record.read(frame, read, frame.size - read)
                          if (n <= 0) {
                              if (stop) return@Thread
                              continue
                          }
                          read += n
                      }
                      val copy = frame.copyOf()
                      val r = trySend(copy)
                      if (r.isFailure) Log.w(TAG, "frame dropped (channel full)")
                  }
              } catch (t: Throwable) {
                  Log.e(TAG, "AudioRecord thread crashed", t)
                  close(t)
              } finally {
                  try { record.stop() } catch (_: Throwable) {}
                  record.release()
              }
          }, "MamY-AudioCapture").apply { isDaemon = true; start() }

          awaitClose {
              stop = true
              thread.interrupt()
              thread.join(500)
          }
      }

      private companion object { const val TAG = "AudioCapture" }
  }
  ```

- [ ] **Step 6.4 — Hilt module**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/AudioModule.kt` :

  ```kotlin
  package com.mamy.android.di

  import com.mamy.android.data.audio.AudioCapture
  import com.mamy.android.data.audio.AudioCaptureImpl
  import dagger.Binds
  import dagger.Module
  import dagger.hilt.InstallIn
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  abstract class AudioModule {
      @Binds @Singleton
      abstract fun bindAudioCapture(impl: AudioCaptureImpl): AudioCapture
  }
  ```

- [ ] **Step 6.5 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

  Expected : BUILD SUCCESSFUL.

- [ ] **Step 6.6 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/audio/ app/src/main/kotlin/com/mamy/android/di/AudioModule.kt
  git commit -m "feat: AudioCapture (16kHz mono PCM, AudioRecord lifecycle, Flow API)"
  ```

---

## Task 7 — `AudioCapture` tests (Robolectric, permission gate)

**Files:**
- `app/src/test/kotlin/com/mamy/android/data/audio/AudioFormatTest.kt`
- `app/src/test/kotlin/com/mamy/android/data/audio/AudioCaptureImplTest.kt`

### Steps

- [ ] **Step 7.1 — AudioFormat constants test**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/audio/AudioFormatTest.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Test

  class AudioFormatTest {

      @Test
      fun `frame size = 480 samples (30 ms @ 16 kHz)`() {
          assertEquals(480, AudioFormat.SAMPLES_PER_FRAME)
      }

      @Test
      fun `frame bytes = 960`() {
          assertEquals(960, AudioFormat.BYTES_PER_FRAME)
      }

      @Test
      fun `max samples = 1_440_000 (90 s @ 16 kHz)`() {
          assertEquals(1_440_000, AudioFormat.MAX_SAMPLES)
      }
  }
  ```

- [ ] **Step 7.2 — Permission-denied test (Robolectric)**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/audio/AudioCaptureImplTest.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertThrows
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class AudioCaptureImplTest {

      @Test
      fun `frames throws SecurityException without RECORD_AUDIO permission`() {
          // Robolectric default : no runtime permissions granted
          val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
          val capture = AudioCaptureImpl(ctx)

          assertThrows(SecurityException::class.java) {
              runBlocking { capture.frames().first() }
          }
      }
  }
  ```

  Note : this is the boundary we can verify cheaply. Real PCM streaming is verified manually (Task 19) since Robolectric doesn't simulate AudioRecord.

- [ ] **Step 7.3 — Run tests**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.audio.*"
  ```

  Expected : 4 tests, all PASS.

- [ ] **Step 7.4 — Commit**

  ```
  git add app/src/test/kotlin/com/mamy/android/data/audio/
  git commit -m "test: AudioCapture format constants + permission-gate"
  ```

---

## Task 8 — WebRTC VAD library integration

**Files:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

We use `com.github.yuriy-budiyev:webrtc-vad-android`, a stable JitPack-published WebRTC VAD wrapper. Alternative : `org.webrtc:webrtc-vad`. We pin `0.0.4` (latest known good).

### Steps

- [ ] **Step 8.1 — Add JitPack to root settings**

  Open `D:/ComfyUI-Intel/mamy/settings.gradle.kts` and ensure `dependencyResolutionManagement.repositories` contains JitPack :

  ```kotlin
  dependencyResolutionManagement {
      repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
      repositories {
          google()
          mavenCentral()
          maven { url = uri("https://jitpack.io") }
      }
  }
  ```

- [ ] **Step 8.2 — Versions**

  Edit `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml`. Add to `[versions]` :

  ```toml
  webrtc-vad = "0.0.4"
  ```

  And to `[libraries]` :

  ```toml
  webrtc-vad = { module = "com.github.yuriy-budiyev:webrtc-vad-android", version.ref = "webrtc-vad" }
  ```

- [ ] **Step 8.3 — Wire dep**

  In `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` `dependencies { ... }` block :

  ```kotlin
  implementation(libs.webrtc.vad)
  ```

- [ ] **Step 8.4 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:dependencies --configuration debugCompileClasspath | grep -i webrtc
  ```

  Expected : a line for `webrtc-vad-android:0.0.4`. If not, the JitPack repo wasn't added or the artifact isn't published — fall back to `com.github.gkonovalov.android-vad:webrtc:2.0.7` (published jcenter mirror) and adjust import paths in Task 9.

- [ ] **Step 8.5 — Commit**

  ```
  git add settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts
  git commit -m "chore: add WebRTC VAD library"
  ```

---

## Task 9 — `VadProcessor` class (consume frames, emit silence event)

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/audio/VadResult.kt`
- `app/src/main/kotlin/com/mamy/android/data/audio/VadProcessor.kt`

### Steps

- [ ] **Step 9.1 — Result sealed class**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/VadResult.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  /**
   * VAD-driven capture outcome.
   * - [Captured] : VAD detected silence > 1.5 s after speech started.
   * - [MaxDuration] : reached 90 s hard cap.
   * - [NoSpeech] : 5 s elapsed with no speech detected → abort.
   */
  sealed interface VadResult {
      val pcm: ShortArray
      val durationSec: Int

      data class Captured(override val pcm: ShortArray, override val durationSec: Int) : VadResult
      data class MaxDuration(override val pcm: ShortArray, override val durationSec: Int) : VadResult
      data class NoSpeech(override val pcm: ShortArray, override val durationSec: Int) : VadResult
  }
  ```

- [ ] **Step 9.2 — VadProcessor**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/VadProcessor.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import com.konovalov.vad.webrtc.Vad
  import com.konovalov.vad.webrtc.config.FrameSize
  import com.konovalov.vad.webrtc.config.Mode
  import com.konovalov.vad.webrtc.config.SampleRate
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.flow.takeWhile
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Consumes a [Flow] of PCM frames and applies VAD :
   *   - waits for first speech frame (otherwise NoSpeech after 5 s)
   *   - then accumulates until 1.5 s of trailing silence
   *   - hard cap at 90 s
   */
  @Singleton
  class VadProcessor @Inject constructor() {

      /** Override-able for testing. */
      internal var vadFactory: () -> SimpleVad = ::createWebRtcVad

      suspend fun captureUntilSilence(frames: Flow<ShortArray>): VadResult {
          val vad = vadFactory()
          try {
              val pcmBuffer = ShortArray(AudioFormat.MAX_SAMPLES)
              var pcmOffset = 0
              var speechSeen = false
              var trailingSilenceFrames = 0
              var noSpeechFrames = 0

              val silenceFramesThreshold =
                  SILENCE_CUT_MS / AudioFormat.FRAME_DURATION_MS  // 1500/30 = 50
              val noSpeechAbortFrames =
                  NO_SPEECH_ABORT_MS / AudioFormat.FRAME_DURATION_MS // 5000/30 = ~166

              var resultMarker: VadResult? = null

              frames.takeWhile { frame ->
                  // Append frame
                  val toCopy = minOf(frame.size, pcmBuffer.size - pcmOffset)
                  System.arraycopy(frame, 0, pcmBuffer, pcmOffset, toCopy)
                  pcmOffset += toCopy

                  // Hard cap
                  if (pcmOffset >= AudioFormat.MAX_SAMPLES) {
                      resultMarker = VadResult.MaxDuration(
                          pcmBuffer.copyOf(pcmOffset), pcmOffset / AudioFormat.SAMPLE_RATE_HZ)
                      return@takeWhile false
                  }

                  val isSpeech = vad.isSpeech(frame)
                  if (isSpeech) {
                      speechSeen = true
                      trailingSilenceFrames = 0
                      noSpeechFrames = 0
                  } else if (speechSeen) {
                      trailingSilenceFrames++
                      if (trailingSilenceFrames >= silenceFramesThreshold) {
                          val sampleCount = pcmOffset - trailingSilenceFrames * AudioFormat.SAMPLES_PER_FRAME
                          val trimmed = pcmBuffer.copyOf(maxOf(sampleCount, 0))
                          resultMarker = VadResult.Captured(trimmed, trimmed.size / AudioFormat.SAMPLE_RATE_HZ)
                          return@takeWhile false
                      }
                  } else {
                      noSpeechFrames++
                      if (noSpeechFrames >= noSpeechAbortFrames) {
                          resultMarker = VadResult.NoSpeech(ShortArray(0), 0)
                          return@takeWhile false
                      }
                  }
                  true
              }.collect { /* drain */ }

              return resultMarker ?: VadResult.Captured(
                  pcmBuffer.copyOf(pcmOffset),
                  pcmOffset / AudioFormat.SAMPLE_RATE_HZ,
              )
          } finally {
              vad.close()
          }
      }

      private fun createWebRtcVad(): SimpleVad {
          val v = Vad.builder()
              .setSampleRate(SampleRate.SAMPLE_RATE_16K)
              .setFrameSize(FrameSize.FRAME_SIZE_480)
              .setMode(Mode.AGGRESSIVE)
              .build()
          return object : SimpleVad {
              override fun isSpeech(frame: ShortArray): Boolean = v.isSpeech(frame)
              override fun close() = v.close()
          }
      }

      private companion object {
          const val SILENCE_CUT_MS = 1500
          const val NO_SPEECH_ABORT_MS = 5000
      }
  }

  /** Test-friendly facade over the WebRTC VAD library. */
  internal interface SimpleVad {
      fun isSpeech(frame: ShortArray): Boolean
      fun close()
  }
  ```

  Note : the import package `com.konovalov.vad.webrtc.*` matches the fallback library `gkonovalov.android-vad:webrtc:2.0.7`. If you used `yuriy-budiyev:webrtc-vad-android`, the package may differ — check the actual artifact and adjust imports. The sealed `SimpleVad` facade is what insulates the rest of the code.

- [ ] **Step 9.3 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

  Expected : BUILD SUCCESSFUL. If imports wrong (different VAD lib package), update imports above in `createWebRtcVad`.

- [ ] **Step 9.4 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/audio/VadResult.kt app/src/main/kotlin/com/mamy/android/data/audio/VadProcessor.kt
  git commit -m "feat: VadProcessor with 1.5s silence cut + 90s cap"
  ```

---

## Task 10 — `VadProcessor` unit tests (mock SimpleVad)

**Files:**
- `app/src/test/kotlin/com/mamy/android/data/audio/VadProcessorTest.kt`

### Steps

- [ ] **Step 10.1 — Test class**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/audio/VadProcessorTest.kt` :

  ```kotlin
  package com.mamy.android.data.audio

  import kotlinx.coroutines.flow.flow
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertTrue
  import org.junit.jupiter.api.Test

  class VadProcessorTest {

      private fun frame(): ShortArray = ShortArray(AudioFormat.SAMPLES_PER_FRAME)

      private fun buildProcessor(decisions: List<Boolean>): VadProcessor {
          val proc = VadProcessor()
          val iterator = decisions.iterator()
          proc.vadFactory = {
              object : SimpleVad {
                  override fun isSpeech(frame: ShortArray): Boolean =
                      if (iterator.hasNext()) iterator.next() else false
                  override fun close() = Unit
              }
          }
          return proc
      }

      @Test
      fun `cuts after 50 frames of trailing silence post-speech`() = runTest {
          // 10 speech frames, then 50 silence frames → Captured
          val decisions = List(10) { true } + List(50) { false }
          val proc = buildProcessor(decisions)
          val source = flow { repeat(decisions.size) { emit(frame()) } }

          val result = proc.captureUntilSilence(source)

          assertTrue(result is VadResult.Captured)
          // 10 speech frames * 480 samples = 4800
          assertEquals(4800, result.pcm.size)
      }

      @Test
      fun `aborts NoSpeech after 5s of pure silence`() = runTest {
          // 200 silence frames > 5000 ms / 30 ms = 167 threshold
          val decisions = List(200) { false }
          val proc = buildProcessor(decisions)
          val source = flow { repeat(decisions.size) { emit(frame()) } }

          val result = proc.captureUntilSilence(source)

          assertTrue("got $result", result is VadResult.NoSpeech)
      }

      @Test
      fun `enforces 90s hard cap as MaxDuration`() = runTest {
          // 3001 speech frames > MAX_SAMPLES / SAMPLES_PER_FRAME = 3000
          val decisions = List(3001) { true }
          val proc = buildProcessor(decisions)
          val source = flow { repeat(decisions.size) { emit(frame()) } }

          val result = proc.captureUntilSilence(source)

          assertTrue("got $result", result is VadResult.MaxDuration)
          assertEquals(90, result.durationSec)
      }
  }
  ```

- [ ] **Step 10.2 — Run tests**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.audio.VadProcessorTest"
  ```

  Expected : 3 tests, all PASS. If `MaxDuration` test fails with off-by-one durationSec, tweak the cap check : `if (pcmOffset >= AudioFormat.MAX_SAMPLES)` — the buffer fills exactly to 1_440_000.

- [ ] **Step 10.3 — Commit**

  ```
  git add app/src/test/kotlin/com/mamy/android/data/audio/VadProcessorTest.kt
  git commit -m "test: VadProcessor cut/abort/cap behaviors"
  ```

---

## Task 11 — NDK + CMake setup for whisper.cpp

**Files:**
- `app/build.gradle.kts`
- `app/src/main/cpp/CMakeLists.txt`
- `app/src/main/cpp/whisper_jni.cpp`
- `app/src/main/cpp/whisper-cpp/` (vendored sources, see step 11.2)
- `gradle.properties`

We vendor `whisper.cpp` at a known commit under `cpp/whisper-cpp/` so builds are reproducible. This adds ~3 MB to repo but is worth the hermetic build.

### Steps

- [ ] **Step 11.1 — Enable NDK in app build**

  In `D:/ComfyUI-Intel/mamy/app/build.gradle.kts`, inside `android { ... }` block :

  ```kotlin
  ndkVersion = "26.3.11579264"  // r26d, matches AGP 8.7
  defaultConfig {
      // … existing fields …
      ndk {
          abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
      }
      externalNativeBuild {
          cmake {
              cppFlags += listOf("-std=c++17", "-O3", "-fPIC", "-DGGML_USE_OPENMP=0")
              arguments += listOf("-DANDROID_STL=c++_static")
          }
      }
  }
  externalNativeBuild {
      cmake {
          path = file("src/main/cpp/CMakeLists.txt")
          version = "3.22.1"
      }
  }
  ```

  In `D:/ComfyUI-Intel/mamy/gradle.properties` add :

  ```
  android.experimental.enableArtProfiles=false
  ```

- [ ] **Step 11.2 — Vendor whisper.cpp sources**

  Run from `D:/ComfyUI-Intel/mamy/` :

  ```
  cd app/src/main/cpp
  git clone --depth 1 --branch v1.7.2 https://github.com/ggerganov/whisper.cpp whisper-cpp
  rm -rf whisper-cpp/.git whisper-cpp/examples whisper-cpp/tests whisper-cpp/bindings whisper-cpp/models whisper-cpp/samples
  ```

  We keep only `whisper-cpp/src/`, `whisper-cpp/include/`, `whisper-cpp/ggml/` for the build. If `--branch v1.7.2` doesn't exist, use latest tag (`git -C whisper-cpp tag | tail -5`) and adjust.

- [ ] **Step 11.3 — CMakeLists.txt**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/cpp/CMakeLists.txt` :

  ```cmake
  cmake_minimum_required(VERSION 3.22.1)
  project("mamy_whisper")

  set(CMAKE_CXX_STANDARD 17)
  set(CMAKE_POSITION_INDEPENDENT_CODE ON)

  set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper-cpp)

  # ggml + whisper sources (paths follow whisper.cpp v1.7.x layout)
  file(GLOB GGML_SRC
      "${WHISPER_DIR}/ggml/src/ggml.c"
      "${WHISPER_DIR}/ggml/src/ggml-alloc.c"
      "${WHISPER_DIR}/ggml/src/ggml-backend.cpp"
      "${WHISPER_DIR}/ggml/src/ggml-quants.c"
      "${WHISPER_DIR}/ggml/src/ggml-cpu.c"
  )
  file(GLOB WHISPER_SRC "${WHISPER_DIR}/src/whisper.cpp")

  add_library(mamy_whisper SHARED
      whisper_jni.cpp
      ${GGML_SRC}
      ${WHISPER_SRC}
  )

  target_include_directories(mamy_whisper PRIVATE
      ${WHISPER_DIR}/include
      ${WHISPER_DIR}/ggml/include
      ${WHISPER_DIR}/ggml/src
      ${WHISPER_DIR}/src
  )

  target_compile_definitions(mamy_whisper PRIVATE
      GGML_USE_OPENMP=0
      _GNU_SOURCE
  )

  find_library(log-lib log)
  target_link_libraries(mamy_whisper ${log-lib})
  ```

  Note : whisper.cpp source layout has shifted across versions. If files don't exist, run `find whisper-cpp -name "ggml*.c"` to find the right paths and update the GLOBs.

- [ ] **Step 11.4 — Stub JNI source (real impl in Task 12)**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/cpp/whisper_jni.cpp` (stub for the build to succeed, full impl next task) :

  ```cpp
  #include <jni.h>
  #include <android/log.h>

  #define LOG_TAG "MamYWhisperJNI"
  #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

  extern "C"
  JNIEXPORT jstring JNICALL
  Java_com_mamy_android_data_stt_jni_WhisperJni_pingNative(
      JNIEnv *env, jobject thiz) {
      LOGI("pingNative called");
      return env->NewStringUTF("ok");
  }
  ```

- [ ] **Step 11.5 — Native build**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:externalNativeBuildDebug
  ```

  Expected : BUILD SUCCESSFUL, output `app/build/intermediates/cxx/Debug/.../obj/<abi>/libmamy_whisper.so` for each ABI. If ggml file paths don't match, fix the CMakeLists GLOBs (Step 11.3) and retry.

- [ ] **Step 11.6 — Commit**

  ```
  git add app/build.gradle.kts gradle.properties app/src/main/cpp/CMakeLists.txt app/src/main/cpp/whisper_jni.cpp app/src/main/cpp/whisper-cpp/
  git commit -m "chore: vendor whisper.cpp v1.7.2 + NDK/CMake setup + JNI stub"
  ```

---

## Task 12 — JNI wrapper Kotlin → C++ for transcribe

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/stt/jni/WhisperJni.kt`
- `app/src/main/cpp/whisper_jni.cpp` (full impl, replaces stub)

### Steps

- [ ] **Step 12.1 — Kotlin JNI surface**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/jni/WhisperJni.kt` :

  ```kotlin
  package com.mamy.android.data.stt.jni

  /**
   * Thin JNI wrapper. Single context per WhisperJni instance; not thread-safe.
   * Lifecycle : [initContext] → many [transcribe] → [freeContext].
   */
  class WhisperJni {
      private var nativePtr: Long = 0L

      fun initContext(modelPath: String) {
          require(nativePtr == 0L) { "Already initialized" }
          nativePtr = initContextNative(modelPath)
          require(nativePtr != 0L) { "whisper_init_from_file failed for $modelPath" }
      }

      /**
       * @param pcm 16-bit PCM @ 16 kHz mono
       * @param language ISO 639-1, e.g. "en", "fr", or "auto"
       */
      fun transcribe(pcm: ShortArray, language: String): String {
          require(nativePtr != 0L) { "Context not initialized" }
          return transcribeNative(nativePtr, pcm, language) ?: ""
      }

      fun freeContext() {
          if (nativePtr != 0L) {
              freeContextNative(nativePtr)
              nativePtr = 0L
          }
      }

      private external fun initContextNative(modelPath: String): Long
      private external fun transcribeNative(ctxPtr: Long, pcm: ShortArray, language: String): String?
      private external fun freeContextNative(ctxPtr: Long)

      external fun pingNative(): String

      companion object {
          init { System.loadLibrary("mamy_whisper") }
      }
  }
  ```

- [ ] **Step 12.2 — Full C++ JNI impl**

  Replace `D:/ComfyUI-Intel/mamy/app/src/main/cpp/whisper_jni.cpp` with :

  ```cpp
  #include <jni.h>
  #include <android/log.h>
  #include <string>
  #include <vector>
  #include "whisper.h"

  #define LOG_TAG "MamYWhisperJNI"
  #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
  #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

  extern "C"
  JNIEXPORT jstring JNICALL
  Java_com_mamy_android_data_stt_jni_WhisperJni_pingNative(JNIEnv *env, jobject) {
      return env->NewStringUTF("ok");
  }

  extern "C"
  JNIEXPORT jlong JNICALL
  Java_com_mamy_android_data_stt_jni_WhisperJni_initContextNative(
      JNIEnv *env, jobject, jstring jModelPath) {
      const char *path = env->GetStringUTFChars(jModelPath, nullptr);
      whisper_context_params cparams = whisper_context_default_params();
      whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
      env->ReleaseStringUTFChars(jModelPath, path);
      if (ctx == nullptr) {
          LOGE("whisper_init_from_file failed for %s", path);
          return 0L;
      }
      LOGI("whisper context loaded: %p", ctx);
      return reinterpret_cast<jlong>(ctx);
  }

  extern "C"
  JNIEXPORT void JNICALL
  Java_com_mamy_android_data_stt_jni_WhisperJni_freeContextNative(
      JNIEnv *, jobject, jlong ctxPtr) {
      auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
      if (ctx) whisper_free(ctx);
  }

  extern "C"
  JNIEXPORT jstring JNICALL
  Java_com_mamy_android_data_stt_jni_WhisperJni_transcribeNative(
      JNIEnv *env, jobject, jlong ctxPtr, jshortArray jPcm, jstring jLang) {
      auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
      if (!ctx) return nullptr;

      jsize n = env->GetArrayLength(jPcm);
      std::vector<float> samples(n);
      jshort *raw = env->GetShortArrayElements(jPcm, nullptr);
      const float kInvScale = 1.0f / 32768.0f;
      for (jsize i = 0; i < n; ++i) samples[i] = static_cast<float>(raw[i]) * kInvScale;
      env->ReleaseShortArrayElements(jPcm, raw, JNI_ABORT);

      const char *lang = env->GetStringUTFChars(jLang, nullptr);

      whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
      wparams.print_progress = false;
      wparams.print_realtime = false;
      wparams.print_timestamps = false;
      wparams.translate = false;
      wparams.language = lang;
      wparams.n_threads = 4;
      wparams.suppress_blank = true;

      int rc = whisper_full(ctx, wparams, samples.data(), static_cast<int>(samples.size()));
      env->ReleaseStringUTFChars(jLang, lang);
      if (rc != 0) {
          LOGE("whisper_full rc=%d", rc);
          return nullptr;
      }

      std::string out;
      const int nseg = whisper_full_n_segments(ctx);
      for (int i = 0; i < nseg; ++i) {
          out += whisper_full_get_segment_text(ctx, i);
      }
      return env->NewStringUTF(out.c_str());
  }
  ```

- [ ] **Step 12.3 — Native rebuild**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:externalNativeBuildDebug
  ```

  Expected : BUILD SUCCESSFUL. If `whisper.h` symbols missing (API drift between whisper.cpp versions), check `whisper-cpp/include/whisper.h` and adjust calls.

- [ ] **Step 12.4 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/stt/jni/WhisperJni.kt app/src/main/cpp/whisper_jni.cpp
  git commit -m "feat: full JNI bridge for whisper.cpp (init/transcribe/free)"
  ```

---

## Task 13 — `WhisperEngine` Kotlin API + first-run model download

**Files:**
- `app/src/main/kotlin/com/mamy/android/data/stt/WhisperModel.kt`
- `app/src/main/kotlin/com/mamy/android/data/stt/WhisperEngine.kt`
- `app/src/main/kotlin/com/mamy/android/data/stt/WhisperEngineImpl.kt`
- `app/src/main/kotlin/com/mamy/android/data/stt/WhisperModelDownloader.kt`
- `app/src/main/kotlin/com/mamy/android/di/SttModule.kt`

We download `ggml-tiny.bin` (~75 MB) on first run from huggingface to keep APK size sane. URL : `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin`.

### Steps

- [ ] **Step 13.1 — Model metadata**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/WhisperModel.kt` :

  ```kotlin
  package com.mamy.android.data.stt

  data class WhisperModel(
      val id: String,
      val fileName: String,
      val downloadUrl: String,
      val sha256: String,
      val expectedBytes: Long,
  ) {
      companion object {
          val TINY = WhisperModel(
              id = "tiny",
              fileName = "ggml-tiny.bin",
              downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
              // sha256 from huggingface for ggml-tiny.bin (whisper.cpp v1.7.x)
              sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
              expectedBytes = 77_691_713L,
          )
      }
  }
  ```

  Note : confirm sha256 against the actual file hosted on HF before V1 ship. If it mismatches at runtime, downloader will reject and re-download once, then surface error.

- [ ] **Step 13.2 — Engine API**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/WhisperEngine.kt` :

  ```kotlin
  package com.mamy.android.data.stt

  import kotlinx.coroutines.flow.Flow

  /** STT operations exposed to the rest of the app. */
  interface WhisperEngine {

      /** True if the model file is on disk and validated. */
      suspend fun isModelReady(): Boolean

      /**
       * Downloads the model if missing. Emits 0..100 progress percentages.
       * Last value is always 100 on success; flow completes after.
       * Throws [java.io.IOException] on network failure or hash mismatch.
       */
      fun downloadModel(): Flow<Int>

      /**
       * Transcribes 16-bit PCM @ 16 kHz mono. [language] is "en", "fr" or "auto".
       * Throws [IllegalStateException] if model not ready.
       */
      suspend fun transcribe(pcm: ShortArray, language: String): Result<String>
  }
  ```

- [ ] **Step 13.3 — Downloader**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/WhisperModelDownloader.kt` :

  ```kotlin
  package com.mamy.android.data.stt

  import android.util.Log
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.flow
  import kotlinx.coroutines.flow.flowOn
  import java.io.File
  import java.io.IOException
  import java.net.HttpURLConnection
  import java.net.URL
  import java.security.MessageDigest

  class WhisperModelDownloader(private val targetDir: File) {

      fun download(model: WhisperModel): Flow<Int> = flow {
          val target = File(targetDir, model.fileName)
          val tmp = File(targetDir, model.fileName + ".part")
          if (target.exists() && verifyHash(target, model.sha256)) {
              emit(100); return@flow
          }
          targetDir.mkdirs()
          tmp.delete()

          val conn = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
              connectTimeout = 30_000
              readTimeout = 60_000
              instanceFollowRedirects = true
          }
          try {
              if (conn.responseCode !in 200..299) {
                  throw IOException("HTTP ${conn.responseCode} fetching ${model.downloadUrl}")
              }
              val total = conn.contentLengthLong.takeIf { it > 0 } ?: model.expectedBytes
              var emittedPct = -1

              conn.inputStream.use { input ->
                  tmp.outputStream().use { output ->
                      val buf = ByteArray(64 * 1024)
                      var read: Long = 0
                      while (true) {
                          val n = input.read(buf)
                          if (n <= 0) break
                          output.write(buf, 0, n)
                          read += n
                          val pct = ((read * 100) / total).toInt().coerceIn(0, 99)
                          if (pct != emittedPct) {
                              emit(pct); emittedPct = pct
                          }
                      }
                  }
              }
          } finally {
              conn.disconnect()
          }

          if (!verifyHash(tmp, model.sha256)) {
              tmp.delete()
              throw IOException("SHA256 mismatch for ${model.fileName}")
          }
          if (!tmp.renameTo(target)) {
              throw IOException("Could not rename ${tmp.absolutePath}")
          }
          emit(100)
      }.flowOn(Dispatchers.IO)

      private fun verifyHash(file: File, expected: String): Boolean {
          val md = MessageDigest.getInstance("SHA-256")
          file.inputStream().use { ins ->
              val buf = ByteArray(64 * 1024)
              while (true) {
                  val n = ins.read(buf)
                  if (n <= 0) break
                  md.update(buf, 0, n)
              }
          }
          val hex = md.digest().joinToString("") { "%02x".format(it) }
          val ok = hex.equals(expected, ignoreCase = true)
          if (!ok) Log.w(TAG, "hash mismatch: got $hex expected $expected")
          return ok
      }

      private companion object { const val TAG = "WhisperDL" }
  }
  ```

- [ ] **Step 13.4 — Engine impl**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/WhisperEngineImpl.kt` :

  ```kotlin
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
  ```

- [ ] **Step 13.5 — Hilt module**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/SttModule.kt` :

  ```kotlin
  package com.mamy.android.di

  import android.content.Context
  import com.mamy.android.data.stt.WhisperEngine
  import com.mamy.android.data.stt.WhisperEngineImpl
  import com.mamy.android.data.stt.WhisperModelDownloader
  import com.mamy.android.data.stt.jni.WhisperJni
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.android.qualifiers.ApplicationContext
  import dagger.hilt.components.SingletonComponent
  import java.io.File
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  object SttModule {

      @Provides @Singleton
      fun provideWhisperJni(): WhisperJni = WhisperJni()

      @Provides @Singleton
      fun provideDownloader(@ApplicationContext ctx: Context): WhisperModelDownloader =
          WhisperModelDownloader(File(ctx.filesDir, "whisper"))

      @Provides @Singleton
      fun provideWhisperEngine(impl: WhisperEngineImpl): WhisperEngine = impl
  }
  ```

- [ ] **Step 13.6 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

  Expected : BUILD SUCCESSFUL.

- [ ] **Step 13.7 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/data/stt/ app/src/main/kotlin/com/mamy/android/di/SttModule.kt
  git commit -m "feat: WhisperEngine + first-run model downloader"
  ```

---

## Task 14 — Whisper smoke test (instrumented, hash check + fixture)

**Files:**
- `app/src/androidTest/assets/test-audio/jfk-mini.pcm` (10 s of "ask not what your country..." PCM 16k mono — we just check the lib loads, not exact transcript)
- `app/src/androidTest/kotlin/com/mamy/android/data/stt/WhisperJniInstrumentedTest.kt`
- `app/src/androidTest/kotlin/com/mamy/android/data/stt/WhisperModelDownloaderInstrumentedTest.kt`

We don't ship a fixture in source (~320 KB). Test gates on `assumeTrue` if missing.

### Steps

- [ ] **Step 14.1 — JNI ping smoke**

  Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/data/stt/WhisperJniInstrumentedTest.kt` :

  ```kotlin
  package com.mamy.android.data.stt

  import androidx.test.ext.junit.runners.AndroidJUnit4
  import com.mamy.android.data.stt.jni.WhisperJni
  import org.junit.Assert.assertEquals
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class WhisperJniInstrumentedTest {

      @Test
      fun `pingNative returns ok (lib loads + JNI hooks correctly)`() {
          val jni = WhisperJni()
          assertEquals("ok", jni.pingNative())
      }
  }
  ```

  This proves : `libmamy_whisper.so` is in the APK, `System.loadLibrary` works, JNI signatures match.

- [ ] **Step 14.2 — Downloader hash test (skipped without network)**

  Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/data/stt/WhisperModelDownloaderInstrumentedTest.kt` :

  ```kotlin
  package com.mamy.android.data.stt

  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import kotlinx.coroutines.flow.last
  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertEquals
  import org.junit.Assume.assumeTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import java.io.File

  @RunWith(AndroidJUnit4::class)
  class WhisperModelDownloaderInstrumentedTest {

      @Test
      fun `download tiny model succeeds and matches expected hash`() = runBlocking {
          assumeTrue(
              "Skipped : set MAMY_RUN_NETWORK=1 to enable network-dependent tests",
              System.getenv("MAMY_RUN_NETWORK") == "1",
          )
          val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
          val dir = File(ctx.filesDir, "whisper-test").also {
              it.deleteRecursively(); it.mkdirs()
          }
          val downloader = WhisperModelDownloader(dir)
          val final = downloader.download(WhisperModel.TINY).last()
          assertEquals(100, final)
          assertEquals(WhisperModel.TINY.expectedBytes, File(dir, WhisperModel.TINY.fileName).length())
      }
  }
  ```

- [ ] **Step 14.3 — Run tests**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:connectedDebugAndroidTest --tests "*WhisperJniInstrumentedTest*"
  ```

  Expected : 1 PASS. If `pingNative` fails with `UnsatisfiedLinkError`, the lib didn't get bundled — re-run `:app:externalNativeBuildDebug` and `assembleDebug` first.

- [ ] **Step 14.4 — Commit**

  ```
  git add app/src/androidTest/kotlin/com/mamy/android/data/stt/
  git commit -m "test: Whisper JNI ping + downloader hash smoke"
  ```

---

## Task 15 — `IntentRouter` stub (always returns CAPTURE)

**Files:**
- `app/src/main/kotlin/com/mamy/android/domain/intent/Intent.kt`
- `app/src/main/kotlin/com/mamy/android/domain/intent/IntentRouter.kt`
- `app/src/test/kotlin/com/mamy/android/domain/intent/IntentRouterTest.kt`

Full grammar is P4. This stub satisfies the wiring contract.

### Steps

- [ ] **Step 15.1 — Sealed Intent**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/intent/Intent.kt` :

  ```kotlin
  package com.mamy.android.domain.intent

  /** All intents recognized by the voice grammar. P2 only emits [CAPTURE]. */
  sealed interface Intent {
      data class Capture(val rawText: String) : Intent
      data object DailyBrief : Intent
      data object NextBrief : Intent
      data class PersonBrief(val person: String) : Intent
      data object PromisesOwedMe : Intent
      data object ActionsOpen : Intent
      data object EodSummary : Intent
      data object UndoLast : Intent
      data class CorrectLast(val correction: String) : Intent
  }
  ```

- [ ] **Step 15.2 — Router stub**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/intent/IntentRouter.kt` :

  ```kotlin
  package com.mamy.android.domain.intent

  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * P2 stub — always returns [Intent.Capture]. P4 will replace with full FR+EN grammar.
   */
  @Singleton
  class IntentRouter @Inject constructor() {
      fun route(transcript: String): Intent = Intent.Capture(transcript)
  }
  ```

- [ ] **Step 15.3 — Test**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/domain/intent/IntentRouterTest.kt` :

  ```kotlin
  package com.mamy.android.domain.intent

  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertTrue
  import org.junit.jupiter.api.Test

  class IntentRouterTest {

      private val router = IntentRouter()

      @Test
      fun `stub always returns Capture`() {
          val result = router.route("MamY, ma journée")
          assertTrue(result is Intent.Capture)
          assertEquals("MamY, ma journée", (result as Intent.Capture).rawText)
      }

      @Test
      fun `stub preserves arbitrary input`() {
          val result = router.route("blah blah blah")
          assertEquals(Intent.Capture("blah blah blah"), result)
      }
  }
  ```

- [ ] **Step 15.4 — Run + commit**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.*"
  ```

  Expected : 2 PASS.

  ```
  git add app/src/main/kotlin/com/mamy/android/domain/intent/ app/src/test/kotlin/com/mamy/android/domain/intent/
  git commit -m "feat: IntentRouter stub (P4 will implement grammar)"
  ```

---

## Task 16 — `CapturePipeline` orchestrator (wake-word → audio → VAD → STT)

**Files:**
- `app/src/main/kotlin/com/mamy/android/domain/capture/CaptureEvent.kt`
- `app/src/main/kotlin/com/mamy/android/domain/capture/CapturePipeline.kt`
- `app/src/test/kotlin/com/mamy/android/domain/capture/CapturePipelineTest.kt`

### Steps

- [ ] **Step 16.1 — Event sealed**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/capture/CaptureEvent.kt` :

  ```kotlin
  package com.mamy.android.domain.capture

  import com.mamy.android.domain.intent.Intent

  /** State events emitted by the capture pipeline. Drives notification icon + UI. */
  sealed interface CaptureEvent {
      data object Idle : CaptureEvent
      data object WakeWordDetected : CaptureEvent
      data object Recording : CaptureEvent
      data object Transcribing : CaptureEvent
      data class TranscriptReady(val text: String, val durationSec: Int, val intent: Intent) : CaptureEvent
      data object NoSpeech : CaptureEvent
      data object MaxDurationHit : CaptureEvent
      data class Error(val cause: Throwable) : CaptureEvent
  }
  ```

- [ ] **Step 16.2 — Pipeline**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/capture/CapturePipeline.kt` :

  ```kotlin
  package com.mamy.android.domain.capture

  import com.mamy.android.data.audio.AudioCapture
  import com.mamy.android.data.audio.VadProcessor
  import com.mamy.android.data.audio.VadResult
  import com.mamy.android.data.stt.WhisperEngine
  import com.mamy.android.domain.intent.IntentRouter
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableSharedFlow
  import kotlinx.coroutines.flow.asSharedFlow
  import kotlinx.coroutines.flow.first
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Single-shot capture orchestrator. After a wake-word fire, the service calls
   * [runOneCapture] which : starts AudioCapture, hands the frame Flow to VadProcessor,
   * passes the resulting PCM to WhisperEngine, then routes the transcript through
   * IntentRouter. All intermediate states are emitted via [events].
   */
  @Singleton
  class CapturePipeline @Inject constructor(
      private val audioCapture: AudioCapture,
      private val vad: VadProcessor,
      private val whisper: WhisperEngine,
      private val router: IntentRouter,
  ) {
      private val _events = MutableSharedFlow<CaptureEvent>(replay = 1, extraBufferCapacity = 16)
      val events: Flow<CaptureEvent> = _events.asSharedFlow()

      init { _events.tryEmit(CaptureEvent.Idle) }

      suspend fun runOneCapture(language: String) {
          try {
              _events.emit(CaptureEvent.Recording)
              val frames = audioCapture.frames()
              val vadResult = vad.captureUntilSilence(frames)

              when (vadResult) {
                  is VadResult.NoSpeech -> {
                      _events.emit(CaptureEvent.NoSpeech)
                      _events.emit(CaptureEvent.Idle)
                      return
                  }
                  is VadResult.MaxDuration -> {
                      _events.emit(CaptureEvent.MaxDurationHit)
                      // continue to transcription with the captured 90 s
                  }
                  is VadResult.Captured -> Unit
              }

              _events.emit(CaptureEvent.Transcribing)
              val sttResult = whisper.transcribe(vadResult.pcm, language)
              sttResult.fold(
                  onSuccess = { text ->
                      val intent = router.route(text)
                      _events.emit(CaptureEvent.TranscriptReady(text, vadResult.durationSec, intent))
                  },
                  onFailure = { _events.emit(CaptureEvent.Error(it)) },
              )
              _events.emit(CaptureEvent.Idle)
          } catch (t: Throwable) {
              _events.emit(CaptureEvent.Error(t))
              _events.emit(CaptureEvent.Idle)
          }
      }

      /** Helper for service to await the next emission. */
      suspend fun nextEvent(): CaptureEvent = events.first()
  }
  ```

- [ ] **Step 16.3 — Test (Turbine)**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/domain/capture/CapturePipelineTest.kt` :

  ```kotlin
  package com.mamy.android.domain.capture

  import app.cash.turbine.test
  import com.mamy.android.data.audio.AudioCapture
  import com.mamy.android.data.audio.AudioFormat
  import com.mamy.android.data.audio.VadProcessor
  import com.mamy.android.data.audio.VadResult
  import com.mamy.android.data.stt.WhisperEngine
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentRouter
  import io.mockk.coEvery
  import io.mockk.every
  import io.mockk.mockk
  import kotlinx.coroutines.flow.emptyFlow
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertTrue
  import org.junit.jupiter.api.Test

  class CapturePipelineTest {

      private val audioCapture: AudioCapture = mockk()
      private val vad: VadProcessor = mockk()
      private val whisper: WhisperEngine = mockk()
      private val router = IntentRouter()

      @Test
      fun `emits Recording, Transcribing, TranscriptReady, Idle on happy path`() = runTest {
          val pcm = ShortArray(16_000) // 1 s
          every { audioCapture.frames() } returns emptyFlow()
          coEvery { vad.captureUntilSilence(any()) } returns VadResult.Captured(pcm, 1)
          coEvery { whisper.transcribe(pcm, "en") } returns Result.success("hello world")

          val pipeline = CapturePipeline(audioCapture, vad, whisper, router)

          pipeline.events.test {
              assertEquals(CaptureEvent.Idle, awaitItem())
              pipeline.runOneCapture("en")
              assertEquals(CaptureEvent.Recording, awaitItem())
              assertEquals(CaptureEvent.Transcribing, awaitItem())
              val tr = awaitItem()
              assertTrue(tr is CaptureEvent.TranscriptReady)
              tr as CaptureEvent.TranscriptReady
              assertEquals("hello world", tr.text)
              assertEquals(1, tr.durationSec)
              assertTrue(tr.intent is Intent.Capture)
              assertEquals(CaptureEvent.Idle, awaitItem())
              cancelAndIgnoreRemainingEvents()
          }
      }

      @Test
      fun `emits NoSpeech then Idle on silent capture`() = runTest {
          every { audioCapture.frames() } returns emptyFlow()
          coEvery { vad.captureUntilSilence(any()) } returns VadResult.NoSpeech(ShortArray(0), 0)

          val pipeline = CapturePipeline(audioCapture, vad, whisper, router)
          pipeline.events.test {
              assertEquals(CaptureEvent.Idle, awaitItem())
              pipeline.runOneCapture("en")
              assertEquals(CaptureEvent.Recording, awaitItem())
              assertEquals(CaptureEvent.NoSpeech, awaitItem())
              assertEquals(CaptureEvent.Idle, awaitItem())
              cancelAndIgnoreRemainingEvents()
          }
      }

      @Test
      fun `emits Error on Whisper failure`() = runTest {
          val pcm = ShortArray(16_000)
          every { audioCapture.frames() } returns emptyFlow()
          coEvery { vad.captureUntilSilence(any()) } returns VadResult.Captured(pcm, 1)
          coEvery { whisper.transcribe(pcm, "en") } returns Result.failure(IllegalStateException("model"))

          val pipeline = CapturePipeline(audioCapture, vad, whisper, router)
          pipeline.events.test {
              assertEquals(CaptureEvent.Idle, awaitItem())
              pipeline.runOneCapture("en")
              assertEquals(CaptureEvent.Recording, awaitItem())
              assertEquals(CaptureEvent.Transcribing, awaitItem())
              val ev = awaitItem()
              assertTrue(ev is CaptureEvent.Error)
              assertEquals(CaptureEvent.Idle, awaitItem())
              cancelAndIgnoreRemainingEvents()
          }
      }

      @Test
      fun `unused length sanity`() {
          assertEquals(480, AudioFormat.SAMPLES_PER_FRAME) // referenced types compile
      }
  }
  ```

- [ ] **Step 16.4 — Run + commit**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.capture.*"
  ```

  Expected : 4 PASS.

  ```
  git add app/src/main/kotlin/com/mamy/android/domain/capture/ app/src/test/kotlin/com/mamy/android/domain/capture/
  git commit -m "feat: CapturePipeline orchestrator + state events"
  ```

---

## Task 17 — Wire `MamYListenerService` (wake-word continuous → pipeline)

**Files:**
- `app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt`
- `app/src/main/kotlin/com/mamy/android/service/CaptureNotification.kt`
- `app/src/main/res/values/strings.xml` (ADD keys)
- `app/src/main/res/values-fr/strings.xml` (ADD keys)
- `app/src/main/res/drawable/ic_mamy_idle.xml`
- `app/src/main/res/drawable/ic_mamy_capturing.xml`

### Steps

- [ ] **Step 17.1 — Notification builder**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/service/CaptureNotification.kt` :

  ```kotlin
  package com.mamy.android.service

  import android.app.Notification
  import android.app.NotificationChannel
  import android.app.NotificationManager
  import android.content.Context
  import androidx.core.app.NotificationCompat
  import com.mamy.android.R
  import com.mamy.android.domain.capture.CaptureEvent

  object CaptureNotification {

      const val CHANNEL_ID = "mamy_listener"
      const val NOTIF_ID = 4242

      fun ensureChannel(ctx: Context) {
          val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
          if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
              val ch = NotificationChannel(
                  CHANNEL_ID,
                  ctx.getString(R.string.mamy_listener_channel_name),
                  NotificationManager.IMPORTANCE_LOW,
              ).apply {
                  description = ctx.getString(R.string.mamy_listener_channel_desc)
                  setShowBadge(false)
              }
              mgr.createNotificationChannel(ch)
          }
      }

      fun build(ctx: Context, event: CaptureEvent): Notification {
          val (iconRes, contentRes) = when (event) {
              is CaptureEvent.Idle, is CaptureEvent.Error,
              is CaptureEvent.NoSpeech -> R.drawable.ic_mamy_idle to R.string.notif_idle
              is CaptureEvent.WakeWordDetected,
              is CaptureEvent.Recording -> R.drawable.ic_mamy_capturing to R.string.notif_recording
              is CaptureEvent.Transcribing -> R.drawable.ic_mamy_capturing to R.string.notif_transcribing
              is CaptureEvent.TranscriptReady,
              is CaptureEvent.MaxDurationHit -> R.drawable.ic_mamy_idle to R.string.notif_idle
          }
          return NotificationCompat.Builder(ctx, CHANNEL_ID)
              .setSmallIcon(iconRes)
              .setContentTitle(ctx.getString(R.string.notif_title))
              .setContentText(ctx.getString(contentRes))
              .setOngoing(true)
              .setOnlyAlertOnce(true)
              .setCategory(NotificationCompat.CATEGORY_SERVICE)
              .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
              .build()
      }
  }
  ```

- [ ] **Step 17.2 — Strings (EN)**

  Append to `D:/ComfyUI-Intel/mamy/app/src/main/res/values/strings.xml` (inside `<resources>...</resources>`) :

  ```xml
  <string name="mamy_listener_channel_name">MamY listener</string>
  <string name="mamy_listener_channel_desc">Always-on wake-word listener</string>
  <string name="notif_title">MamY</string>
  <string name="notif_idle">Listening for "MamY"</string>
  <string name="notif_recording">Recording…</string>
  <string name="notif_transcribing">Transcribing…</string>
  ```

- [ ] **Step 17.3 — Strings (FR)**

  Append to `D:/ComfyUI-Intel/mamy/app/src/main/res/values-fr/strings.xml` :

  ```xml
  <string name="mamy_listener_channel_name">Écoute MamY</string>
  <string name="mamy_listener_channel_desc">Écoute en continu du mot-clé</string>
  <string name="notif_title">MamY</string>
  <string name="notif_idle">À l\'écoute de « MamY »</string>
  <string name="notif_recording">Enregistrement…</string>
  <string name="notif_transcribing">Transcription…</string>
  ```

- [ ] **Step 17.4 — Drawables**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/res/drawable/ic_mamy_idle.xml` :

  ```xml
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp" android:height="24dp"
      android:viewportWidth="24" android:viewportHeight="24"
      android:tint="?attr/colorControlNormal">
      <path android:fillColor="#7f8c8d" android:pathData="M12,2A6,6 0 0 0 6,8v4a6,6 0 0 0 12,0V8A6,6 0 0 0 12,2Z"/>
      <path android:fillColor="#7f8c8d" android:pathData="M19,12a1,1 0 0 0 -2,0a5,5 0 0 1 -10,0a1,1 0 0 0 -2,0a7,7 0 0 0 6,6.92V21a1,1 0 0 0 2,0V18.92A7,7 0 0 0 19,12Z"/>
  </vector>
  ```

  Create `D:/ComfyUI-Intel/mamy/app/src/main/res/drawable/ic_mamy_capturing.xml` :

  ```xml
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp" android:height="24dp"
      android:viewportWidth="24" android:viewportHeight="24">
      <path android:fillColor="#27ae60" android:pathData="M12,2A6,6 0 0 0 6,8v4a6,6 0 0 0 12,0V8A6,6 0 0 0 12,2Z"/>
      <path android:fillColor="#27ae60" android:pathData="M19,12a1,1 0 0 0 -2,0a5,5 0 0 1 -10,0a1,1 0 0 0 -2,0a7,7 0 0 0 6,6.92V21a1,1 0 0 0 2,0V18.92A7,7 0 0 0 19,12Z"/>
  </vector>
  ```

- [ ] **Step 17.5 — Service implementation**

  Replace contents of `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt` :

  ```kotlin
  package com.mamy.android.service

  import android.app.Service
  import android.content.Intent
  import android.content.pm.ServiceInfo
  import android.os.Build
  import android.os.IBinder
  import android.util.Log
  import com.mamy.android.data.wakeword.WakeWordEngine
  import com.mamy.android.data.wakeword.WakeWordSensitivity
  import com.mamy.android.domain.capture.CaptureEvent
  import com.mamy.android.domain.capture.CapturePipeline
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

      private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      private val captureMutex = Mutex()
      @Volatile private var captureJob: Job? = null

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
                  if (ev is CaptureEvent.TranscriptReady) {
                      Log.i(TAG, "TRANSCRIPT: ${ev.text} (intent=${ev.intent}, dur=${ev.durationSec}s)")
                  }
              }
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
  ```

- [ ] **Step 17.6 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

  Expected : BUILD SUCCESSFUL.

- [ ] **Step 17.7 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/service/ app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/drawable/
  git commit -m "feat: wire MamYListenerService — wake-word + pipeline + notif states"
  ```

---

## Task 18 — Volume-up long-press fallback (KeyEvent receiver in Activity)

Long-press detection must happen at the Activity level (Android KeyEvent only delivered to focused window). The service is started by sending `ACTION_TRIGGER_CAPTURE` to bypass wake-word.

**Files:**
- `app/src/main/kotlin/com/mamy/android/MainActivity.kt`
- `app/src/main/kotlin/com/mamy/android/util/VolumeLongPressDetector.kt`
- `app/src/test/kotlin/com/mamy/android/util/VolumeLongPressDetectorTest.kt`

### Steps

- [ ] **Step 18.1 — Detector logic (testable)**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/util/VolumeLongPressDetector.kt` :

  ```kotlin
  package com.mamy.android.util

  /**
   * Detects ≥1000 ms hold on the same key. Pure logic, no Android imports → unit-testable.
   */
  class VolumeLongPressDetector(
      private val thresholdMs: Long = 1000L,
      private val onLongPress: () -> Unit,
      private val now: () -> Long = System::currentTimeMillis,
  ) {
      private var pressStartMs: Long = 0L
      private var fired: Boolean = false
      private var trackedKey: Int = -1

      /** @return true if the event was consumed (long-press fired). */
      fun onKeyDown(keyCode: Int): Boolean {
          if (keyCode != trackedKey) {
              trackedKey = keyCode
              pressStartMs = now()
              fired = false
              return false
          }
          // Repeat events while held
          if (!fired && now() - pressStartMs >= thresholdMs) {
              fired = true
              onLongPress()
              return true
          }
          return false
      }

      fun onKeyUp(keyCode: Int) {
          if (keyCode == trackedKey) {
              trackedKey = -1
              pressStartMs = 0L
              fired = false
          }
      }
  }
  ```

- [ ] **Step 18.2 — Unit test**

  Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/util/VolumeLongPressDetectorTest.kt` :

  ```kotlin
  package com.mamy.android.util

  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertFalse
  import org.junit.jupiter.api.Assertions.assertTrue
  import org.junit.jupiter.api.Test

  class VolumeLongPressDetectorTest {

      private val volUp = 24 // KeyEvent.KEYCODE_VOLUME_UP

      @Test
      fun `fires after 1000 ms of repeats`() {
          var fakeTime = 0L
          var fired = 0
          val det = VolumeLongPressDetector(
              thresholdMs = 1000,
              onLongPress = { fired++ },
              now = { fakeTime },
          )

          assertFalse(det.onKeyDown(volUp))           // start, t=0
          fakeTime = 500
          assertFalse(det.onKeyDown(volUp))           // still held, t=500
          fakeTime = 1001
          assertTrue(det.onKeyDown(volUp))            // crossed threshold
          assertEquals(1, fired)
      }

      @Test
      fun `does not fire if released before threshold`() {
          var fakeTime = 0L
          var fired = 0
          val det = VolumeLongPressDetector(1000, { fired++ }, { fakeTime })
          det.onKeyDown(volUp)
          fakeTime = 800
          det.onKeyUp(volUp)
          fakeTime = 1500
          det.onKeyDown(volUp) // new press
          assertEquals(0, fired)
      }

      @Test
      fun `fires only once per press`() {
          var fakeTime = 0L
          var fired = 0
          val det = VolumeLongPressDetector(1000, { fired++ }, { fakeTime })
          det.onKeyDown(volUp); fakeTime = 1100
          det.onKeyDown(volUp) // fires
          fakeTime = 1500
          det.onKeyDown(volUp) // does NOT re-fire
          assertEquals(1, fired)
      }
  }
  ```

- [ ] **Step 18.3 — MainActivity wiring**

  Edit `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MainActivity.kt`. Add (or merge) :

  ```kotlin
  package com.mamy.android

  import android.content.Intent
  import android.os.Bundle
  import android.view.KeyEvent
  import androidx.activity.ComponentActivity
  import com.mamy.android.service.MamYListenerService
  import com.mamy.android.util.VolumeLongPressDetector
  import dagger.hilt.android.AndroidEntryPoint

  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {

      private val volDetector = VolumeLongPressDetector(onLongPress = ::triggerCapture)

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          // P7 will set Compose content here. P2 keeps the empty activity.
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
  ```

  Note : pure long-press fallback only works when MainActivity is in foreground. For background fallback (manager mid-1:1), V1.1 will add an Accessibility Service. Documented limitation.

- [ ] **Step 18.4 — Run tests**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.util.VolumeLongPressDetectorTest" :app:assembleDebug
  ```

  Expected : 3 unit PASS, BUILD SUCCESSFUL.

- [ ] **Step 18.5 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/util/ app/src/main/kotlin/com/mamy/android/MainActivity.kt app/src/test/kotlin/com/mamy/android/util/
  git commit -m "feat: volume-up long-press fallback (Activity-foreground only V1)"
  ```

---

## Task 19 — Permissions runtime request flow

**Files:**
- `app/src/main/kotlin/com/mamy/android/util/PermissionLauncher.kt`
- `app/src/main/kotlin/com/mamy/android/MainActivity.kt` (additions)

### Steps

- [ ] **Step 19.1 — Permission helper**

  Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/util/PermissionLauncher.kt` :

  ```kotlin
  package com.mamy.android.util

  import android.Manifest
  import android.content.Context
  import android.content.pm.PackageManager
  import android.os.Build
  import androidx.activity.ComponentActivity
  import androidx.activity.result.contract.ActivityResultContracts
  import androidx.core.content.ContextCompat

  object PermissionLauncher {

      val REQUIRED: Array<String> = buildList {
          add(Manifest.permission.RECORD_AUDIO)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              add(Manifest.permission.POST_NOTIFICATIONS)
          }
          if (Build.VERSION.SDK_INT >= 34) {
              add("android.permission.FOREGROUND_SERVICE_MICROPHONE")
          }
      }.toTypedArray()

      fun missing(ctx: Context): List<String> = REQUIRED.filter {
          ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
      }

      fun register(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit) =
          activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
              onResult(results.values.all { it })
          }
  }
  ```

- [ ] **Step 19.2 — Wire in Activity**

  In `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MainActivity.kt`, add inside the class :

  ```kotlin
  private val permLauncher = PermissionLauncher.register(this) { granted ->
      if (granted) startService(android.content.Intent(this, MamYListenerService::class.java))
  }
  ```

  And inside `onCreate(...)` after `super.onCreate(...)` :

  ```kotlin
  val missing = PermissionLauncher.missing(this)
  if (missing.isEmpty()) {
      startService(Intent(this, MamYListenerService::class.java))
  } else {
      permLauncher.launch(missing.toTypedArray())
  }
  ```

  Add the import `import com.mamy.android.util.PermissionLauncher` at top.

- [ ] **Step 19.3 — Build check**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:assembleDebug
  ```

- [ ] **Step 19.4 — Commit**

  ```
  git add app/src/main/kotlin/com/mamy/android/util/PermissionLauncher.kt app/src/main/kotlin/com/mamy/android/MainActivity.kt
  git commit -m "feat: runtime permission request flow (RECORD_AUDIO + POST_NOTIFICATIONS + FGS_MIC)"
  ```

---

## Task 20 — Battery / leak smoke (5 min idle, instrumented)

**Files:**
- `app/src/androidTest/kotlin/com/mamy/android/service/IdleListenerSmokeTest.kt`

### Steps

- [ ] **Step 20.1 — Test**

  Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/service/IdleListenerSmokeTest.kt` :

  ```kotlin
  package com.mamy.android.service

  import android.app.ActivityManager
  import android.content.Context
  import android.content.Intent
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import org.junit.Assert.assertTrue
  import org.junit.Assume.assumeTrue
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class IdleListenerSmokeTest {

      @Test
      fun `5min idle does not double RAM (porcupine + service stable)`() {
          assumeTrue("Long test : set MAMY_RUN_LONG=1", System.getenv("MAMY_RUN_LONG") == "1")
          val ctx = ApplicationProvider.getApplicationContext<Context>()
          val intent = Intent(ctx, MamYListenerService::class.java)
          ctx.startForegroundService(intent)

          Thread.sleep(2_000)
          val before = totalPss(ctx)

          Thread.sleep(5 * 60_000L)

          val after = totalPss(ctx)
          ctx.stopService(intent)

          // Allow up to 50 % growth (warmup + JIT). Anything > 100 % flags a leak.
          assertTrue(
              "PSS doubled : before=$before after=$after",
              after.toDouble() / before <= 2.0,
          )
      }

      private fun totalPss(ctx: Context): Int {
          val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
          val pid = android.os.Process.myPid()
          val info = am.getProcessMemoryInfo(intArrayOf(pid))
          return info[0].totalPss
      }
  }
  ```

  Note : test gated on env `MAMY_RUN_LONG=1` — it takes 5 minutes. Run manually before V1 ship.

- [ ] **Step 20.2 — Commit**

  ```
  git add app/src/androidTest/kotlin/com/mamy/android/service/IdleListenerSmokeTest.kt
  git commit -m "test: 5-min idle leak smoke (gated)"
  ```

---

## Task 21 — End-to-end manual smoke + handoff to P3

This task is human-in-the-loop : install, speak, observe logcat. No code, just verification.

### Steps

- [ ] **Step 21.1 — Build, install, run**

  ```
  cd D:/ComfyUI-Intel/mamy && ./gradlew :app:installDebug
  adb shell am start -n com.mamy.android/.MainActivity
  ```

  Grant RECORD_AUDIO + POST_NOTIFICATIONS in the system dialog when it appears.

- [ ] **Step 21.2 — Confirm wake-word startup**

  ```
  adb logcat -s MamYListenerService PorcupineEngine
  ```

  Expected lines (in order, within ~5 s of launch) :

  - `PorcupineEngine: Porcupine started, keyword=...mamy_en.ppn sens=0.55`
  - `MamYListenerService: event=Idle`

  Pull-down notif drawer : "MamY · Listening for "MamY"" with grey mic icon.

- [ ] **Step 21.3 — Verify wake-word fires**

  Speak clearly : *"MamY"* (just the wake-word, then pause).

  Expected log :

  - `MamYListenerService: event=Recording` → notif icon flips green, text "Recording…"

- [ ] **Step 21.4 — Verify full pipeline**

  Speak : *"MamY, test one two three"* then stay silent ~2 s.

  Expected log sequence within ~5-7 s :

  ```
  event=Recording
  event=Transcribing
  TRANSCRIPT: <approximate text> (intent=Capture(rawText=...), dur=2s)
  event=Idle
  ```

  Even if transcript is partially garbled (whisper-tiny isn't perfect), the pipeline state machine must complete cleanly.

- [ ] **Step 21.5 — Verify volume-up fallback**

  With MainActivity in foreground, hold volume-up for 1.5 s. Expected : `event=Recording` without saying "MamY".

- [ ] **Step 21.6 — Verify silence abort**

  Say *"MamY"* and immediately stay silent 6 s. Expected : `event=NoSpeech` then `event=Idle`.

- [ ] **Step 21.7 — Verify max-duration cap**

  Say *"MamY"* and continuously talk for 95 seconds. Expected : `event=MaxDurationHit` then `event=Transcribing` then `event=TranscriptReady` with `dur=90`.

- [ ] **Step 21.8 — If all 6 verifications pass**

  Tag the pipeline complete :

  ```
  git tag p2-voice-capture-complete
  git push --tags
  ```

  Push branch :

  ```
  git push -u origin p2-voice-capture
  ```

  Open PR `p2-voice-capture` → `main`. P3 (LLM structurer + DB writeback) consumes `CaptureEvent.TranscriptReady` from `CapturePipeline.events`.

---

## Final checklist

Before declaring P2 done, all of the following must be true :

- [ ] All unit tests green : `./gradlew :app:testDebugUnitTest`
- [ ] All instrumented tests green (or skipped via assume) : `./gradlew :app:connectedDebugAndroidTest`
- [ ] Lint clean : `./gradlew :app:lintDebug` (warn only, no errors)
- [ ] Manual smoke (Task 21) all 6 sub-steps pass
- [ ] No TODO / TBD / FIXME comments left in P2 code (`grep -rn "TODO\|FIXME\|TBD" app/src/main/kotlin/com/mamy/android/{data/{audio,wakeword,stt},domain/{capture,intent},service,util}/`)
- [ ] Branch `p2-voice-capture` rebased on latest `main`
- [ ] Tag `p2-voice-capture-complete` exists
- [ ] PR opened against `main`

## Hand-off notes for P3

- `CapturePipeline.events: Flow<CaptureEvent>` is the public contract P3 consumes
- On `CaptureEvent.TranscriptReady` with `intent is Intent.Capture`, P3 will :
  1. Send `text` to BYOK LLM with the structuration prompt (spec §5)
  2. Parse JSON, write `Note` + `Action` + `Promise` + `Flag` rows via Room DAOs (P1)
  3. TTS confirmation (P6 dependency, but stub OK : Android `TextToSpeech.speak("Noté")`)
- IntentRouter stub will be replaced in P4 — for now P3 only handles Capture intent
- `WhisperEngine.transcribe` returns `Result<String>` — surface failures as user-visible TTS error (P3 concern)
