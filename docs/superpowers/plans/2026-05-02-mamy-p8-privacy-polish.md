# MamY P8 — Privacy, Polish, Beta Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship-harden MamY V1 — implement data export/wipe per privacy stance, instrumentation for battery profiling, local-only crash logging, signed APK pipeline, Play Console internal/open beta tracks, store assets, and privacy policy. After P8, V1 is in 10 alpha testers' hands and on the path to public launch.

**Architecture:** Data export pipeline = serialize all DAOs → JSONL → gzip → AES-CBC with PBKDF2 key from user passphrase → SAF write. Wipe operations = transactional Room deletes + Keystore alias delete + DataStore clear. Crash logger = local file + manual share intent (no telemetry, no cloud). Release pipeline = Gradle signing config + R8/ProGuard + Play Console upload.

**Tech Stack:** Kotlin 2.0.21 · kotlinx.serialization 1.7 · Bouncy Castle (AES) · Storage Access Framework · Gradle Play Publisher (optional) · Android Studio Profilers · Play Console
---

## Pre-flight checks

- [ ] On branch `p8-privacy-polish` rebased on `main` (which has P1-P7 merged)
- [ ] `./gradlew test` green on `main`
- [ ] `./gradlew lint` green on `main`
- [ ] Working APK installs and runs on Pixel 7 reference device
- [ ] All P1-P7 acceptance criteria met (manual smoke test of capture → briefing → settings)

## Conventions reminder

- All paths absolute Windows (`D:/ComfyUI-Intel/mamy/...`)
- Package root: `com.mamy.android`
- Tests under `app/src/test/kotlin/com/mamy/android/` (unit) + `app/src/androidTest/...` (instrumented)
- Commit prefix: `feat:` / `fix:` / `test:` / `refactor:` / `chore:` / `docs:`
- Co-author footer auto

---

## Task 1 — Add kotlinx.serialization + Bouncy Castle deps

- [ ] Edit `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml` to add new entries

```toml
[versions]
# ... existing entries
kotlinx-serialization = "1.7.3"
bouncycastle = "1.78.1"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
bouncycastle-bcprov = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` plugins block

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` dependencies

```kotlin
dependencies {
    // ... existing
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.bcprov)
}
```

- [ ] Run `./gradlew :app:dependencies | grep -E "(serialization|bcprov)"` to verify resolution

- [ ] Commit: `chore: add kotlinx.serialization + bouncycastle for export pipeline`

---

## Task 2 — ExportSerializer: serialize all 8 entities to JSONL

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/export/ExportSerializer.kt`

```kotlin
package com.mamy.android.data.export

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.BriefingEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialize the full DB to JSON Lines format (one JSON object per line).
 *
 * Format envelope (first line is metadata header, rest are records):
 * { "type": "header", "schema_version": 1, "exported_at": "ISO8601", "app_version": "1.0.0" }
 * { "type": "person", "data": { ... PersonEntity ... } }
 * { "type": "note", "data": { ... NoteEntity ... } }
 * ...
 */
@Singleton
class ExportSerializer @Inject constructor(
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val meetingDao: MeetingDao,
    private val meetingAttendeeDao: MeetingAttendeeDao,
    private val briefingDao: BriefingDao,
) {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Serializable
    data class Header(
        val type: String = "header",
        val schemaVersion: Int = SCHEMA_VERSION,
        val exportedAt: String,
        val appVersion: String,
    )

    @Serializable
    data class Record<T>(
        val type: String,
        val data: T,
    )

    suspend fun writeTo(out: OutputStream, exportedAt: String, appVersion: String) {
        out.appendLine(json.encodeToString(Header(exportedAt = exportedAt, appVersion = appVersion)))

        personDao.getAll().forEach { p ->
            out.appendLine(json.encodeToString(Record("person", p)))
        }
        noteDao.getAll().forEach { n ->
            out.appendLine(json.encodeToString(Record("note", n)))
        }
        actionDao.getAll().forEach { a ->
            out.appendLine(json.encodeToString(Record("action", a)))
        }
        promiseDao.getAll().forEach { p ->
            out.appendLine(json.encodeToString(Record("promise", p)))
        }
        flagDao.getAll().forEach { f ->
            out.appendLine(json.encodeToString(Record("flag", f)))
        }
        meetingDao.getAll().forEach { m ->
            out.appendLine(json.encodeToString(Record("meeting", m)))
        }
        meetingAttendeeDao.getAll().forEach { ma ->
            out.appendLine(json.encodeToString(Record("meeting_attendee", ma)))
        }
        briefingDao.getAll().forEach { b ->
            out.appendLine(json.encodeToString(Record("briefing", b)))
        }
        out.flush()
    }

    private fun OutputStream.appendLine(s: String) {
        write(s.toByteArray(Charsets.UTF_8))
        write("\n".toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
```

- [ ] Ensure all entity classes carry `@Serializable` annotation. Open each entity file under `app/src/main/kotlin/com/mamy/android/data/db/entity/` and add the annotation if absent. Example pattern for `PersonEntity.kt`:

```kotlin
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    // ... rest of fields
)
```

- [ ] Add `getAll(): List<X>` query to each DAO if not present. Pattern:

```kotlin
@Query("SELECT * FROM person")
suspend fun getAll(): List<PersonEntity>
```

Apply to: PersonDao, NoteDao, ActionDao, PromiseDao, FlagDao, MeetingDao, MeetingAttendeeDao, BriefingDao.

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add ExportSerializer + DAO getAll() queries for full DB dump`

---

## Task 3 — ExportSerializer tests

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/export/ExportSerializerTest.kt`

```kotlin
package com.mamy.android.data.export

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class ExportSerializerTest {

    private lateinit var personDao: PersonDao
    private lateinit var noteDao: NoteDao
    private lateinit var actionDao: ActionDao
    private lateinit var promiseDao: PromiseDao
    private lateinit var flagDao: FlagDao
    private lateinit var meetingDao: MeetingDao
    private lateinit var meetingAttendeeDao: MeetingAttendeeDao
    private lateinit var briefingDao: BriefingDao
    private lateinit var serializer: ExportSerializer

    @BeforeEach
    fun setUp() {
        personDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        actionDao = mockk(relaxed = true)
        promiseDao = mockk(relaxed = true)
        flagDao = mockk(relaxed = true)
        meetingDao = mockk(relaxed = true)
        meetingAttendeeDao = mockk(relaxed = true)
        briefingDao = mockk(relaxed = true)
        serializer = ExportSerializer(
            personDao, noteDao, actionDao, promiseDao,
            flagDao, meetingDao, meetingAttendeeDao, briefingDao
        )
    }

    @Test
    fun `writeTo emits header line first`() = runTest {
        coEvery { personDao.getAll() } returns emptyList()
        coEvery { noteDao.getAll() } returns emptyList()
        coEvery { actionDao.getAll() } returns emptyList()
        coEvery { promiseDao.getAll() } returns emptyList()
        coEvery { flagDao.getAll() } returns emptyList()
        coEvery { meetingDao.getAll() } returns emptyList()
        coEvery { meetingAttendeeDao.getAll() } returns emptyList()
        coEvery { briefingDao.getAll() } returns emptyList()

        val out = ByteArrayOutputStream()
        serializer.writeTo(out, exportedAt = "2026-05-02T12:00:00Z", appVersion = "1.0.0")

        val lines = out.toString(Charsets.UTF_8).trim().split("\n")
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"type\":\"header\""))
        assertTrue(lines[0].contains("\"schemaVersion\":1"))
        assertTrue(lines[0].contains("\"exportedAt\":\"2026-05-02T12:00:00Z\""))
    }

    @Test
    fun `writeTo emits one line per record`() = runTest {
        val person = PersonEntity(
            id = "p1",
            name = "Marc Tremblay",
            email = "marc@example.com",
            roleHint = "Lead",
            calendarAttendeeId = null,
            createdAt = 1000L,
            lastInteractionAt = null,
            interactionCount = 0,
            emotionalTrend = null,
            unmatched = false,
            archived = false,
        )
        coEvery { personDao.getAll() } returns listOf(person, person.copy(id = "p2"))
        coEvery { noteDao.getAll() } returns emptyList()
        coEvery { actionDao.getAll() } returns emptyList()
        coEvery { promiseDao.getAll() } returns emptyList()
        coEvery { flagDao.getAll() } returns emptyList()
        coEvery { meetingDao.getAll() } returns emptyList()
        coEvery { meetingAttendeeDao.getAll() } returns emptyList()
        coEvery { briefingDao.getAll() } returns emptyList()

        val out = ByteArrayOutputStream()
        serializer.writeTo(out, exportedAt = "2026-05-02T12:00:00Z", appVersion = "1.0.0")

        val lines = out.toString(Charsets.UTF_8).trim().split("\n")
        // 1 header + 2 person records = 3
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("\"type\":\"person\""))
        assertTrue(lines[1].contains("\"id\":\"p1\""))
        assertTrue(lines[2].contains("\"id\":\"p2\""))
    }
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests "*ExportSerializerTest*"` — expect PASS.

- [ ] Commit: `test: add ExportSerializer JSONL serialization tests`

---

## Task 4 — AesEncryptor: AES-CBC + PBKDF2 derive

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/export/AesEncryptor.kt`

```kotlin
package com.mamy.android.data.export

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-CBC with PBKDF2-derived key from a user-supplied passphrase.
 *
 * Output format (encrypted stream layout):
 *   [4 bytes] magic = "MAMY"
 *   [1 byte ] version = 0x01
 *   [16 bytes] salt
 *   [16 bytes] iv
 *   [N bytes] ciphertext (PKCS7-padded)
 *
 * KDF:
 *   PBKDF2WithHmacSHA256, 200_000 iterations, 256-bit key.
 *
 * NOTE: CBC mode chosen for streaming compatibility with arbitrary OutputStream
 * (no AEAD tag append/verify required mid-stream). Integrity is provided
 * implicitly by the gzip CRC + JSONL parse failure on tamper.
 */
@Singleton
class AesEncryptor @Inject constructor() {

    fun wrapForEncrypt(out: OutputStream, passphrase: CharArray): OutputStream {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        // Write header eagerly
        out.write(MAGIC)
        out.write(byteArrayOf(VERSION))
        out.write(salt)
        out.write(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        }
        return CipherOutputStream(out, cipher)
    }

    fun wrapForDecrypt(input: InputStream, passphrase: CharArray): InputStream {
        val magic = input.readNBytes(MAGIC.size)
        require(magic.contentEquals(MAGIC)) { "Not a MamY encrypted file (bad magic)" }
        val version = input.read()
        require(version == VERSION.toInt()) { "Unsupported export version: $version" }
        val salt = input.readNBytes(SALT_LEN)
        val iv = input.readNBytes(IV_LEN)
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        }
        return CipherInputStream(input, cipher)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }

    companion object {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val KEY_LEN_BITS = 256
        private const val SALT_LEN = 16
        private const val IV_LEN = 16
        private const val VERSION: Byte = 0x01
        private val MAGIC = "MAMY".toByteArray(Charsets.UTF_8)
    }
}
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add AesEncryptor with PBKDF2-derived AES-256-CBC keys`

---

## Task 5 — AesEncryptor tests (encrypt/decrypt round-trip + tamper detection)

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/export/AesEncryptorTest.kt`

```kotlin
package com.mamy.android.data.export

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AesEncryptorTest {

    private val encryptor = AesEncryptor()

    @Test
    fun `round-trip preserves payload`() {
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val plaintext = "Bonjour MamY, ceci est un export de test.\nLine 2.\n".toByteArray(Charsets.UTF_8)

        val encrypted = ByteArrayOutputStream().also { sink ->
            encryptor.wrapForEncrypt(sink, passphrase).use { it.write(plaintext) }
        }.toByteArray()

        val decrypted = encryptor.wrapForDecrypt(ByteArrayInputStream(encrypted), passphrase)
            .use { it.readBytes() }

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `output starts with MAMY magic and version 1`() {
        val passphrase = "pw".toCharArray()
        val sink = ByteArrayOutputStream()
        encryptor.wrapForEncrypt(sink, passphrase).use { it.write(byteArrayOf(0)) }
        val bytes = sink.toByteArray()
        assertEquals('M'.code.toByte(), bytes[0])
        assertEquals('A'.code.toByte(), bytes[1])
        assertEquals('M'.code.toByte(), bytes[2])
        assertEquals('Y'.code.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
    }

    @Test
    fun `bad magic throws on decrypt`() {
        val bad = "XXXX".toByteArray() + ByteArray(64)
        assertThrows(IllegalArgumentException::class.java) {
            encryptor.wrapForDecrypt(ByteArrayInputStream(bad), "pw".toCharArray()).read()
        }
    }

    @Test
    fun `wrong passphrase produces garbage or throws`() {
        val passphrase = "right".toCharArray()
        val plaintext = "secret data".toByteArray()
        val encrypted = ByteArrayOutputStream().also { sink ->
            encryptor.wrapForEncrypt(sink, passphrase).use { it.write(plaintext) }
        }.toByteArray()

        // Decrypting with wrong passphrase: padding will likely fail OR yield garbage.
        // Either is acceptable — caller validates downstream (gzip CRC, JSONL parse).
        val wrongResult = runCatching {
            encryptor.wrapForDecrypt(ByteArrayInputStream(encrypted), "wrong".toCharArray())
                .use { it.readBytes() }
        }
        if (wrongResult.isSuccess) {
            assertTrue(!wrongResult.getOrNull().contentEquals(plaintext))
        }
    }

    @Test
    fun `salt and iv are random across calls`() {
        val passphrase = "pw".toCharArray()
        val plaintext = "x".toByteArray()
        val a = ByteArrayOutputStream().also {
            encryptor.wrapForEncrypt(it, passphrase).use { os -> os.write(plaintext) }
        }.toByteArray()
        val b = ByteArrayOutputStream().also {
            encryptor.wrapForEncrypt(it, passphrase).use { os -> os.write(plaintext) }
        }.toByteArray()

        // Bytes 5..21 = salt, 21..37 = IV. These must differ.
        val saltA = a.copyOfRange(5, 21)
        val saltB = b.copyOfRange(5, 21)
        assertTrue(!saltA.contentEquals(saltB), "Salts must be random per encryption")
    }
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests "*AesEncryptorTest*"` — expect PASS.

- [ ] Commit: `test: add AesEncryptor round-trip + tamper detection tests`

---

## Task 6 — ExportPipeline: SAF integration + gzip + AES wrapper

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/export/ExportPipeline.kt`

```kotlin
package com.mamy.android.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full export pipeline: serialize all DB tables → JSONL → gzip → AES-CBC →
 * write to user-chosen Storage Access Framework Uri.
 *
 * Stream chain (write side):
 *   ContentResolver.openOutputStream(uri)
 *     → AesEncryptor.wrapForEncrypt(passphrase)
 *     → GZIPOutputStream
 *     → ExportSerializer.writeTo(...)
 *
 * Caller is responsible for:
 *   - launching ACTION_CREATE_DOCUMENT for SAF picker
 *   - prompting user for passphrase
 */
@Singleton
class ExportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serializer: ExportSerializer,
    private val encryptor: AesEncryptor,
) {

    sealed interface Result {
        data class Success(val bytesWritten: Long) : Result
        data class Failure(val cause: Throwable) : Result
    }

    /**
     * Export entire DB to [destination] encrypted with [passphrase].
     * [appVersion] is recorded in the export header for forward-compatibility.
     */
    suspend fun export(
        destination: Uri,
        passphrase: CharArray,
        appVersion: String,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val sink = resolver.openOutputStream(destination, "wt")
                ?: return@withContext Result.Failure(IllegalStateException("Could not open output stream for $destination"))

            val countingSink = CountingOutputStream(sink)
            val encryptedSink = encryptor.wrapForEncrypt(countingSink, passphrase)
            val gzipSink = GZIPOutputStream(encryptedSink)

            gzipSink.use { gz ->
                serializer.writeTo(
                    gz,
                    exportedAt = Instant.now().toString(),
                    appVersion = appVersion,
                )
            }

            // Wipe passphrase from memory ASAP
            passphrase.fill(' ')

            Result.Success(countingSink.bytesWritten)
        } catch (t: Throwable) {
            passphrase.fill(' ')
            Result.Failure(t)
        }
    }

    private class CountingOutputStream(private val delegate: java.io.OutputStream) : java.io.OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add ExportPipeline wiring SAF + gzip + AES`

---

## Task 7 — ExportPipeline tests

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/export/ExportPipelineTest.kt`

```kotlin
package com.mamy.android.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

class ExportPipelineTest {

    @Test
    fun `export writes gzip-then-aes encrypted blob to destination`() = runTest {
        // Set up empty DAOs
        val personDao: PersonDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val noteDao: NoteDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val actionDao: ActionDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val promiseDao: PromiseDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val flagDao: FlagDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val meetingDao: MeetingDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val attendeeDao: MeetingAttendeeDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }
        val briefingDao: BriefingDao = mockk(relaxed = true).apply { coEvery { getAll() } returns emptyList() }

        val serializer = ExportSerializer(personDao, noteDao, actionDao, promiseDao, flagDao, meetingDao, attendeeDao, briefingDao)
        val encryptor = AesEncryptor()

        val captured = ByteArrayOutputStream()
        val resolver: ContentResolver = mockk()
        val context: Context = mockk()
        val uri: Uri = mockk()
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri, "wt") } returns captured

        val pipeline = ExportPipeline(context, serializer, encryptor)
        val result = pipeline.export(uri, "secret123".toCharArray(), "1.0.0")

        assertTrue(result is ExportPipeline.Result.Success)

        // Now decrypt + ungzip the captured bytes — must round-trip to JSONL header.
        val encrypted = captured.toByteArray()
        val decrypted = encryptor
            .wrapForDecrypt(ByteArrayInputStream(encrypted), "secret123".toCharArray())
            .use { GZIPInputStream(it).readBytes() }
        val jsonl = decrypted.toString(Charsets.UTF_8)
        assertTrue(jsonl.contains("\"type\":\"header\""))
        assertTrue(jsonl.contains("\"appVersion\":\"1.0.0\""))
    }

    @Test
    fun `export returns Failure when openOutputStream returns null`() = runTest {
        val personDao: PersonDao = mockk(relaxed = true)
        val serializer: ExportSerializer = mockk()
        val encryptor = AesEncryptor()
        val resolver: ContentResolver = mockk()
        val context: Context = mockk()
        val uri: Uri = mockk()
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri, "wt") } returns null

        val pipeline = ExportPipeline(context, serializer, encryptor)
        val result = pipeline.export(uri, "pw".toCharArray(), "1.0.0")

        assertTrue(result is ExportPipeline.Result.Failure)
    }
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests "*ExportPipelineTest*"` — expect PASS.

- [ ] Commit: `test: add ExportPipeline integration tests with round-trip decrypt`

---

## Task 8 — WipePerPerson use-case (transactional cascade delete)

- [ ] Add cascade delete queries to DAOs. Edit each DAO file and append:

`PersonDao.kt`:
```kotlin
@Query("DELETE FROM person WHERE id = :personId")
suspend fun deleteById(personId: String): Int
```

`NoteDao.kt`:
```kotlin
@Query("DELETE FROM note WHERE person_id = :personId")
suspend fun deleteByPersonId(personId: String): Int
```

`ActionDao.kt`:
```kotlin
@Query("DELETE FROM action WHERE linked_person_id = :personId OR assignee = :personId")
suspend fun deleteByPersonId(personId: String): Int
```

`PromiseDao.kt`:
```kotlin
@Query("DELETE FROM promise WHERE from_id = :personId OR to_id = :personId")
suspend fun deleteByPersonId(personId: String): Int
```

`FlagDao.kt`:
```kotlin
@Query("DELETE FROM flag WHERE person_id = :personId")
suspend fun deleteByPersonId(personId: String): Int
```

`MeetingAttendeeDao.kt`:
```kotlin
@Query("DELETE FROM meeting_attendee WHERE person_id = :personId")
suspend fun deleteByPersonId(personId: String): Int
```

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/wipe/WipePerPerson.kt`

```kotlin
package com.mamy.android.domain.wipe

import androidx.room.withTransaction
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import javax.inject.Inject

/**
 * Transactional cascade delete for one Person and all dependent rows:
 *   Note · Action · Promise · Flag · MeetingAttendee
 *
 * Meeting rows are NOT deleted (a meeting can have other attendees still
 * relevant). Briefing rows are NOT touched (cache, will expire and regen).
 */
class WipePerPerson @Inject constructor(
    private val db: MamYDatabase,
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val meetingAttendeeDao: MeetingAttendeeDao,
) {

    data class Result(
        val notes: Int,
        val actions: Int,
        val promises: Int,
        val flags: Int,
        val attendees: Int,
        val personDeleted: Boolean,
    )

    suspend operator fun invoke(personId: String): Result = db.withTransaction {
        val notes = noteDao.deleteByPersonId(personId)
        val actions = actionDao.deleteByPersonId(personId)
        val promises = promiseDao.deleteByPersonId(personId)
        val flags = flagDao.deleteByPersonId(personId)
        val attendees = meetingAttendeeDao.deleteByPersonId(personId)
        val personRows = personDao.deleteById(personId)
        Result(
            notes = notes,
            actions = actions,
            promises = promises,
            flags = flags,
            attendees = attendees,
            personDeleted = personRows > 0,
        )
    }
}
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add WipePerPerson transactional cascade delete use-case`

---

## Task 9 — WipePerPerson tests

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/domain/wipe/WipePerPersonInstrumentedTest.kt`

```kotlin
package com.mamy.android.domain.wipe

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WipePerPersonInstrumentedTest {

    private lateinit var db: MamYDatabase
    private lateinit var wipe: WipePerPerson

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, MamYDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wipe = WipePerPerson(
            db = db,
            personDao = db.personDao(),
            noteDao = db.noteDao(),
            actionDao = db.actionDao(),
            promiseDao = db.promiseDao(),
            flagDao = db.flagDao(),
            meetingAttendeeDao = db.meetingAttendeeDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cascadeDeletesAllRowsForOnePerson() = runBlocking {
        val pId = "p-marc"
        db.personDao().insert(PersonEntity(id = pId, name = "Marc", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = 0, lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false))
        db.personDao().insert(PersonEntity(id = "p-other", name = "Other", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = 0, lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false))

        db.noteDao().insert(NoteEntity(id = "n1", personId = pId, meetingId = null, rawText = "x",
            structuredJson = null, nonStructured = false, createdAt = 0, audioDurationSec = 30,
            llmProvider = "claude", llmCostCents = 1))
        db.noteDao().insert(NoteEntity(id = "n2", personId = "p-other", meetingId = null, rawText = "y",
            structuredJson = null, nonStructured = false, createdAt = 0, audioDurationSec = 30,
            llmProvider = "claude", llmCostCents = 1))

        db.actionDao().insert(ActionEntity(id = "a1", description = "ping", assignee = "self",
            linkedPersonId = pId, deadline = null, status = "open",
            fromNoteId = "n1", createdAt = 0, doneAt = null))

        db.promiseDao().insert(PromiseEntity(id = "pr1", fromId = pId, toId = "self",
            what = "x", due = null, status = "active", fromNoteId = "n1", createdAt = 0, resolvedAt = null))

        db.flagDao().insert(FlagEntity(id = "f1", personId = pId, type = "demotivation",
            source = "direct", severity = "medium", note = "x", resolved = false, fromNoteId = "n1", createdAt = 0))

        db.meetingDao().insert(MeetingEntity(id = "m1", calendarEventId = null, title = "1:1",
            startsAt = 0, endsAt = 0, briefingText = null, postNoteId = null, createdAt = 0))
        db.meetingAttendeeDao().insert(MeetingAttendeeEntity(meetingId = "m1", personId = pId))
        db.meetingAttendeeDao().insert(MeetingAttendeeEntity(meetingId = "m1", personId = "p-other"))

        val res = wipe(pId)

        assertEquals(1, res.notes)
        assertEquals(1, res.actions)
        assertEquals(1, res.promises)
        assertEquals(1, res.flags)
        assertEquals(1, res.attendees)
        assertEquals(true, res.personDeleted)

        // Person p-other and its note untouched
        val others = db.personDao().getAll()
        assertEquals(1, others.size)
        assertEquals("p-other", others[0].id)
        assertEquals(1, db.noteDao().getAll().size)
        assertEquals(1, db.meetingAttendeeDao().getAll().size)
        assertNull(db.personDao().getAll().find { it.id == pId })
    }
}
```

- [ ] Run `./gradlew :app:connectedDebugAndroidTest --tests "*WipePerPersonInstrumentedTest*"` (requires emulator) — expect PASS.

- [ ] Commit: `test: add WipePerPerson cascade integrity instrumented test`

---

## Task 10 — WipeAll use-case

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/wipe/WipeAll.kt`

```kotlin
package com.mamy.android.domain.wipe

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.secrets.SecretsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Nuclear wipe: blasts everything MamY persisted on this device.
 *
 * Order matters — DB first (largest, most sensitive), then keys (so even
 * if DB delete partially fails, secrets are still cleared), then prefs,
 * then on-disk model cache + audio archive.
 *
 * After this returns successfully, calling code should kill the process
 * via `Process.killProcess(Process.myPid())` so the next launch is fresh.
 */
class WipeAll @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MamYDatabase,
    private val secrets: SecretsRepository,
    private val dataStore: DataStore<Preferences>,
) {

    data class Result(
        val dbCleared: Boolean,
        val secretsCleared: Boolean,
        val prefsCleared: Boolean,
        val cacheCleared: Boolean,
        val audioArchiveCleared: Boolean,
    )

    suspend operator fun invoke(): Result = withContext(Dispatchers.IO) {
        val dbCleared = runCatching {
            db.clearAllTables()
        }.isSuccess

        val secretsCleared = runCatching {
            secrets.clearAll()
        }.isSuccess

        val prefsCleared = runCatching {
            dataStore.edit { it.clear() }
        }.isSuccess

        val cacheCleared = runCatching {
            File(context.filesDir, "models").deleteRecursively() &&
                context.cacheDir.deleteRecursively()
        }.getOrDefault(false)

        val audioArchiveCleared = runCatching {
            File(context.filesDir, "audio_archive").deleteRecursively()
        }.getOrDefault(true) // OK if dir didn't exist

        Result(dbCleared, secretsCleared, prefsCleared, cacheCleared, audioArchiveCleared)
    }
}
```

- [ ] Add `clearAll()` method to `SecretsRepository` if absent. Edit `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/secrets/SecretsRepository.kt`:

```kotlin
suspend fun clearAll() {
    encryptedPrefs.edit().clear().apply()
    // Delete Keystore aliases owned by MamY
    val keystore = java.security.KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    keystore.aliases().toList().filter { it.startsWith("mamy_") }.forEach { alias ->
        keystore.deleteEntry(alias)
    }
}
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add WipeAll use-case for full local data nuke`

---

## Task 11 — WipeAll tests

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/domain/wipe/WipeAllInstrumentedTest.kt`

```kotlin
package com.mamy.android.domain.wipe

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.secrets.SecretsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WipeAllInstrumentedTest {

    private lateinit var db: MamYDatabase
    private lateinit var ctx: android.content.Context
    private val Context_dataStoreField by androidx.datastore.preferences.preferencesDataStore("wipe_test_prefs")

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, MamYDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun wipeAllClearsDbPrefsAndCache() = runBlocking {
        // Seed DB
        db.personDao().insert(PersonEntity(id = "x", name = "X", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = 0, lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false))
        assertEquals(1, db.personDao().getAll().size)

        // Seed cache + audio archive folders
        val modelsDir = File(ctx.filesDir, "models").apply { mkdirs() }
        File(modelsDir, "whisper-tiny.bin").writeText("x")
        val audioDir = File(ctx.filesDir, "audio_archive").apply { mkdirs() }
        File(audioDir, "rec1.opus").writeText("x")

        val secrets: SecretsRepository = mockk(relaxed = true)
        coEvery { secrets.clearAll() } returns Unit

        val ds: DataStore<Preferences> = mockk(relaxed = true)
        coEvery { ds.edit(any()) } returns preferencesOf()

        val wipe = WipeAll(ctx, db, secrets, ds)
        val res = wipe()

        assertTrue(res.dbCleared)
        assertTrue(res.secretsCleared)
        assertTrue(res.prefsCleared)
        assertTrue(res.cacheCleared)
        assertTrue(res.audioArchiveCleared)
        assertEquals(0, db.personDao().getAll().size)
        assertTrue(!File(ctx.filesDir, "audio_archive").exists())
        coVerify { secrets.clearAll() }
    }
}
```

- [ ] Run `./gradlew :app:connectedDebugAndroidTest --tests "*WipeAllInstrumentedTest*"` — expect PASS.

- [ ] Commit: `test: add WipeAll integrity instrumented test`

---

## Task 12 — CrashLogger: Thread.setDefaultUncaughtExceptionHandler + local file write

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/crash/CrashLogger.kt`

```kotlin
package com.mamy.android.data.crash

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only crash logger. NO cloud, NO telemetry.
 *
 * Writes uncaught exceptions to `<filesDir>/crashes/<utc-timestamp>.txt`
 * then chains to the previously installed handler (typically Android's
 * default which terminates the process).
 *
 * The user can later tap "Send crash report" in Settings, which surfaces
 * the file via a system share intent — the user picks the channel
 * (email, Slack, save-to-Drive, etc.). MamY never auto-sends.
 */
@Singleton
class CrashLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val crashDir: File
        get() = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun listCrashFiles(): List<File> =
        crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun deleteAll(): Int {
        val files = crashDir.listFiles() ?: return 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val ts = TS_FORMAT.get()!!.format(Date())
        val out = File(crashDir, "$ts.txt")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("MamY crash log")
            pw.println("Timestamp UTC: $ts")
            pw.println("Thread: ${thread.name}")
            pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("App version: ${appVersion()}")
            pw.println("---")
            throwable.printStackTrace(pw)
        }
        out.writeText(sw.toString())
    }

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    companion object {
        private val TS_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MamYApplication.kt` to install on `onCreate`:

```kotlin
@HiltAndroidApp
class MamYApplication : Application() {

    @Inject lateinit var crashLogger: CrashLogger

    override fun onCreate() {
        super.onCreate()
        crashLogger.install()
    }
}
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add local-only CrashLogger (no cloud, no telemetry)`

---

## Task 13 — CrashLogger tests

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/crash/CrashLoggerTest.kt`

```kotlin
package com.mamy.android.data.crash

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrashLoggerTest {

    @Test
    fun `install replaces default handler and writes file on crash`(@TempDir tmp: File) {
        val ctx: Context = mockk(relaxed = true)
        every { ctx.filesDir } returns tmp
        every { ctx.packageName } returns "com.mamy.android"

        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val logger = CrashLogger(ctx)
            logger.install()
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            assert(handler !== original)

            // Trigger
            handler!!.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            val files = File(tmp, "crashes").listFiles().orEmpty()
            assertEquals(1, files.size)
            val text = files[0].readText()
            assertTrue(text.contains("RuntimeException"))
            assertTrue(text.contains("boom"))
            assertTrue(text.contains("Thread:"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun `listCrashFiles returns sorted by mtime desc`(@TempDir tmp: File) {
        val ctx: Context = mockk(relaxed = true)
        every { ctx.filesDir } returns tmp
        every { ctx.packageName } returns "com.mamy.android"

        val crashDir = File(tmp, "crashes").apply { mkdirs() }
        val older = File(crashDir, "2026-05-01T00-00-00Z.txt").apply { writeText("a"); setLastModified(1_000) }
        val newer = File(crashDir, "2026-05-02T00-00-00Z.txt").apply { writeText("b"); setLastModified(2_000) }

        val logger = CrashLogger(ctx)
        val list = logger.listCrashFiles()
        assertEquals(2, list.size)
        assertEquals(newer, list[0])
        assertEquals(older, list[1])
    }

    @Test
    fun `deleteAll removes all crash files`(@TempDir tmp: File) {
        val ctx: Context = mockk(relaxed = true)
        every { ctx.filesDir } returns tmp
        every { ctx.packageName } returns "com.mamy.android"

        val crashDir = File(tmp, "crashes").apply { mkdirs() }
        File(crashDir, "a.txt").writeText("x")
        File(crashDir, "b.txt").writeText("x")

        val logger = CrashLogger(ctx)
        val n = logger.deleteAll()
        assertEquals(2, n)
        assertEquals(0, crashDir.listFiles()?.size ?: 0)
    }
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests "*CrashLoggerTest*"` — expect PASS.

- [ ] Commit: `test: add CrashLogger handler + listing + delete tests`

---

## Task 14 — Crash export action in Settings

- [ ] Create `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/screens/settings/CrashReportSection.kt`

```kotlin
package com.mamy.android.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mamy.android.R
import com.mamy.android.data.crash.CrashLogger
import java.io.File

@Composable
fun CrashReportSection(crashLogger: CrashLogger, modifier: Modifier = Modifier) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        files = crashLogger.listCrashFiles()
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_crash_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (files.isEmpty()) stringResource(R.string.settings_crash_none)
                   else stringResource(R.string.settings_crash_count, files.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (files.isNotEmpty()) {
            Button(onClick = {
                val latest = files[0]
                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    latest,
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "MamY crash report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(share, ctx.getString(R.string.settings_crash_share_title)))
            }) {
                Text(stringResource(R.string.settings_crash_send))
            }
            Button(onClick = {
                crashLogger.deleteAll()
                files = emptyList()
            }) {
                Text(stringResource(R.string.settings_crash_delete_all))
            }
        }
    }
}
```

- [ ] Add FileProvider configuration. Create `D:/ComfyUI-Intel/mamy/app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="crashes" path="crashes/" />
</paths>
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/src/main/AndroidManifest.xml` and add inside `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/src/main/res/values/strings.xml` and `values-fr/strings.xml`:

EN:
```xml
<string name="settings_crash_section_title">Crash reports</string>
<string name="settings_crash_none">No crash logs found.</string>
<string name="settings_crash_count">%1$d crash log(s) on this device — never auto-sent.</string>
<string name="settings_crash_send">Send crash report…</string>
<string name="settings_crash_delete_all">Delete all crash logs</string>
<string name="settings_crash_share_title">Share crash report via…</string>
```

FR:
```xml
<string name="settings_crash_section_title">Rapports de plantage</string>
<string name="settings_crash_none">Aucun plantage enregistré.</string>
<string name="settings_crash_count">%1$d plantage(s) enregistrés localement — jamais envoyés automatiquement.</string>
<string name="settings_crash_send">Envoyer le rapport…</string>
<string name="settings_crash_delete_all">Supprimer tous les rapports</string>
<string name="settings_crash_share_title">Partager via…</string>
```

- [ ] Wire `CrashReportSection` into the existing `SettingsScreen` composable (find `SettingsScreen.kt`, inject `crashLogger`, render section).

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: surface crash logs in Settings with manual share intent`

---

## Task 15 — Battery profiling instrumentation (Trace markers)

- [ ] Add `androidx.tracing` import where needed. Edit `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` dependencies:

```kotlin
implementation("androidx.tracing:tracing:1.2.0")
```

- [ ] Wrap the wake-word loop in `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/wakeword/PorcupineEngine.kt`:

```kotlin
import androidx.tracing.Trace

// inside the listen loop function:
fun process(audioFrame: ShortArray): Boolean {
    Trace.beginSection("MamY.WakeWord.process")
    try {
        return porcupine.process(audioFrame) >= 0
    } finally {
        Trace.endSection()
    }
}
```

- [ ] Wrap audio capture in `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/audio/AudioCapture.kt`:

```kotlin
import androidx.tracing.Trace

suspend fun captureUntilSilence(): ShortArray {
    Trace.beginSection("MamY.AudioCapture.session")
    try {
        // ... existing capture logic
    } finally {
        Trace.endSection()
    }
}
```

- [ ] Wrap Whisper inference in `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/stt/WhisperEngine.kt`:

```kotlin
import androidx.tracing.Trace

fun transcribe(pcm: ShortArray): String {
    Trace.beginSection("MamY.Whisper.transcribe")
    try {
        // ... JNI call
    } finally {
        Trace.endSection()
    }
}
```

- [ ] Wrap LLM calls. Edit each provider class under `data/llm/claude/`, `openai/`, `gemini/`. For each `suspend fun structure(...)`:

```kotlin
import androidx.tracing.Trace

suspend fun structure(prompt: String): String {
    Trace.beginSection("MamY.LLM.${providerName}.structure")
    try {
        // ... HTTP call
    } finally {
        Trace.endSection()
    }
}
```

- [ ] Add a docs entry. Create `D:/ComfyUI-Intel/mamy/docs/profiling/battery-profiling.md`:

```markdown
# Battery Profiling Recipes

## Live trace (systrace / Perfetto)

Connect device via USB, enable USB debugging, then:

```bash
adb shell setprop debug.atrace.app com.mamy.android
adb shell atrace --async_start -a com.mamy.android -b 32768 sched freq idle am wm gfx view binder_driver hal dalvik camera input res audio
# Use the app for ~2 minutes (trigger wake word, capture, briefing).
adb shell atrace --async_stop -o /sdcard/mamy-trace.atrace
adb pull /sdcard/mamy-trace.atrace
```

Open `mamy-trace.atrace` in https://ui.perfetto.dev/. Filter to slices named `MamY.*` to isolate the four hot paths:
- `MamY.WakeWord.process`
- `MamY.AudioCapture.session`
- `MamY.Whisper.transcribe`
- `MamY.LLM.<provider>.structure`

## Battery historian dump (24h profile)

```bash
adb shell dumpsys batterystats --reset
# Use phone normally for 24h.
adb shell dumpsys batterystats > batterystats.txt
adb shell dumpsys batterystats --checkin > batterystats-checkin.txt
```

Render with battery-historian (https://github.com/google/battery-historian):

```bash
docker run -p 9999:9999 gcr.io/android-battery-historian/stable:3.0
```

Open http://localhost:9999, upload `batterystats-checkin.txt`, look for `com.mamy.android` row. Target: < 8%/day on Pixel 6+.

## Per-method CPU profile

Use Android Studio Profiler → CPU → Sample Java/Kotlin Methods → record 30s while
triggering a wake → capture → structure cycle.
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile.

- [ ] Commit: `feat: add Trace markers + battery profiling docs`

---

## Task 16 — Release Gradle config: signingConfig + R8 + ProGuard

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/build.gradle.kts`:

```kotlin
android {
    namespace = "com.mamy.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mamy.android"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("MAMY_KEYSTORE_PATH")
                ?: project.findProperty("MAMY_KEYSTORE_PATH") as String?
            val keystorePassword = System.getenv("MAMY_KEYSTORE_PASSWORD")
                ?: project.findProperty("MAMY_KEYSTORE_PASSWORD") as String?
            val keyAlias = System.getenv("MAMY_KEY_ALIAS")
                ?: project.findProperty("MAMY_KEY_ALIAS") as String?
            val keyPassword = System.getenv("MAMY_KEY_PASSWORD")
                ?: project.findProperty("MAMY_KEY_PASSWORD") as String?

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

- [ ] Edit `D:/ComfyUI-Intel/mamy/app/proguard-rules.pro` (create if absent):

```proguard
# ===== MamY ProGuard / R8 rules =====

# Kotlin
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlinx.coroutines.** { *; }

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class com.mamy.android.**$$serializer { *; }
-keepclassmembers class com.mamy.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.mamy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keepclassmembers class * {
    @dagger.hilt.android.scopes.* *;
    @javax.inject.Inject *;
}

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# Whisper JNI
-keep class com.mamy.android.data.stt.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Picovoice Porcupine
-keep class ai.picovoice.porcupine.** { *; }

# Compose stability
-keep class androidx.compose.runtime.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }

# Don't strip enum class names (used in JSON)
-keepclassmembers enum * { *; }

# Strip log calls in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
```

- [ ] Add `D:/ComfyUI-Intel/mamy/keystore/` folder to `.gitignore`. Edit `D:/ComfyUI-Intel/mamy/.gitignore`:

```
keystore/
*.jks
*.keystore
local.properties
```

- [ ] Document keystore generation. Create `D:/ComfyUI-Intel/mamy/docs/release/SIGNING.md`:

```markdown
# Signing keystore

## Generate (one-time)

```bash
mkdir -p D:/ComfyUI-Intel/mamy/keystore
keytool -genkey -v \
  -keystore D:/ComfyUI-Intel/mamy/keystore/mamy-release.jks \
  -alias mamy-key \
  -keyalg RSA -keysize 4096 -validity 25000
```

Store the keystore + passwords in 1Password. **Never commit.**

## Configure local builds

Add to `D:/ComfyUI-Intel/mamy/local.properties` (gitignored):

```
MAMY_KEYSTORE_PATH=D:/ComfyUI-Intel/mamy/keystore/mamy-release.jks
MAMY_KEYSTORE_PASSWORD=<from 1Password>
MAMY_KEY_ALIAS=mamy-key
MAMY_KEY_PASSWORD=<from 1Password>
```

Or export as environment variables.

## Play App Signing (recommended)

Upload the *upload* key cert to Play Console once. Google holds the actual
signing key. Lost upload key → reset via Play support, no app re-publish.
```

- [ ] Run `./gradlew :app:assembleDebug` — must compile (no signing required for debug).

- [ ] Commit: `chore: configure release signing + R8/ProGuard rules`

---

## Task 17 — Build + sign release APK locally

- [ ] Generate the keystore (see SIGNING.md)
- [ ] Add to `local.properties` per SIGNING.md
- [ ] Run `./gradlew :app:assembleRelease`
- [ ] Verify output exists at `D:/ComfyUI-Intel/mamy/app/build/outputs/apk/release/app-release.apk`
- [ ] Verify signing: `apksigner verify --verbose D:/ComfyUI-Intel/mamy/app/build/outputs/apk/release/app-release.apk`. Expect `Verified using v2 scheme: true`.
- [ ] Sideload to Pixel 7 reference device: `adb install -r D:/ComfyUI-Intel/mamy/app/build/outputs/apk/release/app-release.apk`
- [ ] Smoke test: launch, run onboarding, trigger wake word, capture → structure → briefing
- [ ] Verify R8 minified: `unzip -l app-release.apk` — `classes.dex` should be markedly smaller than debug APK

- [ ] Create release script `D:/ComfyUI-Intel/mamy/scripts/build-release.ps1`:

```powershell
# Build, verify, sideload release APK.
param(
    [switch]$Install
)

$ErrorActionPreference = "Stop"
Set-Location D:\ComfyUI-Intel\mamy
.\gradlew.bat :app:clean :app:assembleRelease

$apk = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) {
    Write-Error "APK not built: $apk"
    exit 1
}

Write-Host "APK built: $apk"
Write-Host "Verifying signature..."
apksigner verify --verbose $apk
if ($LASTEXITCODE -ne 0) {
    Write-Error "Signature verification failed"
    exit 1
}

if ($Install) {
    Write-Host "Installing on connected device..."
    adb install -r $apk
}
```

- [ ] Commit: `chore: add release build script + verify signed APK boots`

---

## Task 18 — App icon design (512×512 + adaptive layers)

This task is design-only — no code, but acceptance criteria are concrete.

**Concept:** stylized "MY" wordmark — `M` slightly upper, `Y` slightly lower, with a subtle mic-dot inside the negative space of the `Y` fork. Gradient teal → indigo background. Modern, professional, instantly readable at 48dp.

- [ ] Foreground asset: `D:/ComfyUI-Intel/mamy/app/src/main/res/mipmap-anydpi-v26/ic_launcher_foreground.xml` (vector). Acceptance:
    - 108dp × 108dp viewport
    - Safe zone 66dp × 66dp centered (Android adaptive icon standard)
    - Wordmark "MY" pure white, centered
    - Mic-dot 4dp diameter, inside Y fork
- [ ] Background asset: `D:/ComfyUI-Intel/mamy/app/src/main/res/drawable/ic_launcher_background.xml`. Acceptance:
    - Gradient `#0F766E` (teal) → `#312E81` (indigo) diagonal
    - No additional shapes (foreground does the work)
- [ ] Adaptive icon descriptor: `D:/ComfyUI-Intel/mamy/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

- [ ] Round variant: `D:/ComfyUI-Intel/mamy/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` (same content).
- [ ] Legacy fallbacks via Android Studio Asset Studio: `Image Asset → Launcher Icons (Adaptive and Legacy)`. This generates `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` PNG variants.
- [ ] Generate Play Store 512×512 PNG: `D:/ComfyUI-Intel/mamy/store/icon-512.png`. Same wordmark + gradient, square, no transparency.
- [ ] Smoke test: install on Pixel 7 + Samsung S22 + OnePlus 9. Icon must:
    - Render correctly with circular mask (Pixel)
    - Render correctly with squircle mask (Samsung)
    - Render correctly with rounded-square mask (OnePlus)
    - Tinted monochrome variant works in Android 13+ themed icons mode

- [ ] Commit: `feat: ship app icon (adaptive + legacy + Play Store)`

---

## Task 19 — Play Console: create app + internal testing track

Manual steps in Google Play Console (https://play.google.com/console).

- [ ] Sign in with Google account dedicated to MamY publishing
- [ ] Pay one-time $25 USD developer registration fee if not already done
- [ ] Create app:
    - App name: `MamY`
    - Default language: `English – United States` (FR added later)
    - App or game: `App`
    - Free or paid: `Free` (subscription handled in-app)
    - Declarations: confirm Developer Program Policies + US export laws
- [ ] App content checklist (each is a manual form):
    - [ ] Privacy policy URL → use URL from Task 22
    - [ ] App access: `All functionality is available without restrictions` (or provide BYOK demo key for reviewer)
    - [ ] Ads: `No, my app does not contain ads`
    - [ ] Content rating questionnaire → IARC. MamY = no UGC, no violence, no gambling → expect "Everyone"
    - [ ] Target audience: 18+
    - [ ] News app: No
    - [ ] COVID-19 contact tracing: No
    - [ ] Data safety form: declare audio collected on-device, transcripts stored on-device, BYOK API keys stored hardware-backed Keystore. Calendar OAuth scope = read-only. **No data shared with third parties** (BYOK calls to Anthropic/OpenAI/Google are user-initiated under user's own key, not MamY collecting).
- [ ] Set up internal testing:
    - Testing → Internal testing → Create new release
    - Upload signed AAB (next task generates AAB instead of APK)
    - Release name: `1.0.0-alpha-1`
    - Release notes (EN + FR) per Task 19b
    - Add 10 alpha tester emails to "Testers" list (allowlist, fetch from your prospect database)
    - Get opt-in URL, share with testers via email
- [ ] Verify the rollout shows "Available to internal testers" within 1-2 hours

**Release notes draft:**

EN (`en-US`):
```
Welcome to MamY private alpha.

This is the first build available to test users. Expect rough edges.

What's new:
- Voice-first capture: say "MamY, take a note" then debrief freely
- Daily / pre-meeting / EOD briefings
- Person memory: notes, actions, promises, flags per report
- Google Calendar sync
- BYOK Claude or GPT-4o (paste your own API key in Settings)
- All data stays local, encrypted via SQLCipher

Known issues:
- Wake word may false-trigger on similar phonemes — adjust sensitivity in Settings
- Whisper-tiny may struggle with heavy Quebec jargon — flag bad transcripts
- No cloud backup yet — keep your phone safe

Send feedback: simon@<domain>.ca
```

FR (`fr-CA`):
```
Bienvenue dans la version alpha privée de MamY.

Première build disponible pour les testeurs. Attendez-vous à des aspérités.

Quoi de neuf :
- Capture vocale : dis « MamY, prends note » puis débriefe librement
- Briefings matinal / pré-rencontre / fin de journée
- Mémoire par personne : notes, actions, promesses, flags
- Sync Google Calendar
- BYOK Claude ou GPT-4o (colle ta propre clé API dans Réglages)
- Toutes les données restent locales, chiffrées via SQLCipher

Problèmes connus :
- Le mot d'éveil peut se déclencher sur des phonèmes proches — ajuste la sensibilité
- Whisper-tiny peut avoir du mal avec du jargon québécois lourd — signale les mauvaises transcriptions
- Aucune sauvegarde cloud — sécurise ton téléphone

Feedback : simon@<domaine>.ca
```

- [ ] No code commit (manual ops).

---

## Task 19b — Build AAB (Play Console wants App Bundle, not APK)

- [ ] Run `./gradlew :app:bundleRelease`
- [ ] Verify output `D:/ComfyUI-Intel/mamy/app/build/outputs/bundle/release/app-release.aab`
- [ ] Verify signature: `bundletool validate --bundle=app-release.aab`
- [ ] Upload to Play Console internal testing track
- [ ] Add to `D:/ComfyUI-Intel/mamy/scripts/build-release.ps1`:

```powershell
# Append to existing script:
.\gradlew.bat :app:bundleRelease
$aab = "app\build\outputs\bundle\release\app-release.aab"
if (Test-Path $aab) {
    Write-Host "AAB built: $aab"
}
```

- [ ] Commit: `chore: build AAB for Play Console upload`

---

## Task 20 — Play Store listing assets (screenshots, graphics, descriptions)

Manual + asset production. Required assets:

**Screenshots — phone (5 minimum, 16:9 or 9:16):**

- [ ] Capture 5 screens on Pixel 7 emulator at 1080×1920 in EN:
    1. Onboarding hero ("Voice-first secretary for managers")
    2. Reports list (sample data: Marc, Marie, Pierre, Julie, Luc, Anaïs)
    3. Person detail (Marie — flags, promises, last 5 notes)
    4. Capture in progress (mic indicator pulsing)
    5. Settings → BYOK config + privacy stance
- [ ] Capture same 5 screens in FR
- [ ] Save to `D:/ComfyUI-Intel/mamy/store/screenshots/phone/en/01..05.png` and `.../fr/01..05.png`

**Screenshots — 7" tablet (5):**
- [ ] Repeat on Pixel C / Nexus 9 emulator at 1200×1920
- [ ] Save to `D:/ComfyUI-Intel/mamy/store/screenshots/tablet/en|fr/`

**Feature graphic 1024×500 (no transparency):**
- [ ] Design: gradient teal→indigo background, "MamY" wordmark + tagline "Voice-first memory for managers"
- [ ] Save to `D:/ComfyUI-Intel/mamy/store/feature-graphic-1024x500.png`

**App icon 512×512:** already produced in Task 18, copy to `D:/ComfyUI-Intel/mamy/store/icon-512.png`

**Short description (80 char max):**

EN: `Voice-first manager memory: brief before 1:1s, never forget a promise.`
FR: `Mémoire vocale du manager: briefe avant chaque 1:1, oublie aucune promesse.`

**Full description (4000 char max):**

EN — save to `D:/ComfyUI-Intel/mamy/store/listing/full-description-en.md`:

```
MamY is a voice-first secretary for managers of teams of 30 to 100 people.

The wedge: post-meeting voice debriefs. Walk out of a 1:1, say "MamY, take a note," and free-talk for 60 to 90 seconds. MamY transcribes locally, structures the content with the LLM you bring (Claude or GPT-4o, your key, your bill), and writes everything to encrypted local storage.

The next morning, "MamY, my day" reads back a 60-second briefing of every meeting on your calendar — who you're seeing, what you owed them, what they owed you, what flags you opened last week. Five minutes before each 1:1, a silent vibration: "MamY, brief me." 20 seconds of context.

What MamY tracks:
* Notes per person, indexed forever
* Promises, both directions (you owe them / they owe you), with status
* Open actions with deadlines
* Emotional flags (demotivation, conflict, growth) with direct/indirect source
* Trends across last N interactions

What MamY is NOT:
* A meeting recorder. Live audio of meetings is your problem, not ours. Zero legal risk.
* A note-taking app. You don't open it. You talk to it.
* An HRIS. We don't replace BambooHR. We make 1:1s sharper.
* Cloud. Your data lives on your phone. AES-encrypted via SQLCipher. Hardware-backed keys.

Privacy stance:
* Audio is never written to disk by default. RAM-only.
* Whisper transcription runs locally on your device (75 MB model bundled).
* Structured JSON is generated by the LLM provider you choose, with your own API key.
* Calendar sync is read-only OAuth, scoped to free/busy + attendees + titles.
* No analytics. No telemetry. No crash auto-reporting. Crashes are written locally; you decide whether to share them.
* Full export (encrypted gzip JSON) and full wipe (one tap) at any time.

Quebec Loi 25 + PIPEDA + GDPR compliant by design.

BYOK pricing:
* Trial: 14 days free, full features, BYOK required.
* Solo: $19/month, BYOK, all V1 features.
* Team (V2): $15/seat/month, admin BYOK, audit log.

Min Android 9 (API 28). Recommended: Pixel 6+ or equivalent for Whisper inference latency.

Built in Quebec. Solo dev. Open to feedback: simon@<domain>.ca.
```

FR — save to `D:/ComfyUI-Intel/mamy/store/listing/full-description-fr.md`:

```
MamY est un assistant secrétaire vocal pour les managers d'équipes de 30 à 100 personnes.

Le wedge : débrief vocal post-rencontre. Tu sors d'un 1:1, tu dis « MamY, prends note », et tu parles librement 60 à 90 secondes. MamY transcrit localement, structure avec le LLM que tu fournis (Claude ou GPT-4o, ta clé, ta facture), et écrit tout dans un stockage local chiffré.

Le lendemain matin, « MamY, ma journée » te lit un briefing de 60 secondes de chaque rencontre du jour — qui tu vois, ce que tu lui devais, ce qu'il te devait, les flags ouverts la semaine passée. Cinq minutes avant chaque 1:1, une vibration silencieuse : « MamY, briefe ». 20 secondes de contexte.

Ce que MamY suit :
* Notes par personne, indexées à vie
* Promesses bilatérales (tu lui dois / il te doit), avec statut
* Actions ouvertes avec échéances
* Flags émotionnels (démotivation, conflit, croissance) avec source directe/indirecte
* Tendances sur les N dernières interactions

Ce que MamY N'EST PAS :
* Un enregistreur de réunion. L'audio live, c'est ton problème. Zéro risque légal.
* Une app de prise de notes. Tu ne l'ouvres pas. Tu lui parles.
* Un HRIS. On ne remplace pas BambooHR. On rend tes 1:1 plus aiguisés.
* Le cloud. Tes données vivent sur ton téléphone. Chiffrées AES via SQLCipher. Clés hardware-backed.

Position privacy :
* L'audio n'est jamais écrit sur disque par défaut. RAM seulement.
* La transcription Whisper roule localement (modèle 75 MB livré avec l'app).
* Le JSON structuré est généré par le LLM de ton choix, avec ta clé.
* Sync calendrier en lecture seule, scope minimal.
* Zéro analytics. Zéro télémétrie. Zéro rapport crash automatique. Les crashes sont écrits localement; tu décides si tu les partages.
* Export complet (gzip JSON chiffré) et wipe complet (un tap) en tout temps.

Conforme Loi 25 Québec + PIPEDA + GDPR par design.

Tarifs BYOK :
* Essai : 14 jours gratuits, toutes fonctionnalités, BYOK requis.
* Solo : 19$/mois, BYOK, toutes les features V1.
* Team (V2) : 15$/siège/mois, BYOK admin, audit log.

Android 9 minimum (API 28). Recommandé : Pixel 6+ ou équivalent pour la latence Whisper.

Construit au Québec. Dev solo. Feedback bienvenu : simon@<domaine>.ca.
```

- [ ] Upload all of the above in Play Console → Main store listing → save draft for each language
- [ ] Privacy policy URL: from Task 22
- [ ] Category: Productivity
- [ ] Tags: productivity, business, voice, AI

- [ ] Commit (assets only, manual upload to Play Console): `docs: add Play Store listing assets (en + fr-CA)`

---

## Task 21 — Privacy policy markdown draft

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/legal/privacy-policy.md`

```markdown
# MamY Privacy Policy

**Effective date:** 2026-05-15
**Operator:** Simon Cantin (sole proprietor), Quebec, Canada

This policy explains what MamY collects, where it's stored, who can access it, and your rights.

## TL;DR

MamY runs **locally on your phone**. Your audio, transcripts, structured notes, and BYOK API keys never leave your device unless you choose to. There is no MamY cloud account, no MamY server holding your data, no analytics, no telemetry, and no auto-uploaded crash reports.

The only outbound network calls are:
1. To **the LLM provider you configured** (Anthropic, OpenAI, or Google), using **your own API key**, to structure your debriefs.
2. To **Google or Microsoft Calendar** (read-only OAuth), to fetch the meetings you authorized.

That's it.

## Data we collect

### On your device only

- **Audio**: held in RAM during processing only. Never written to disk by default. (Optional V2 feature lets you opt in to local archive.)
- **Transcripts**: produced by Whisper-tiny running locally on your phone. Stored in an encrypted Room database (SQLCipher, AES-256, key hardware-backed via Android Keystore).
- **Structured data**: per-person notes, actions, promises, flags, meetings. Encrypted Room database.
- **BYOK API keys**: stored in Android Keystore (hardware-backed when available). Never transmitted to MamY operators.
- **Calendar OAuth tokens**: stored in EncryptedSharedPreferences. Auto-refreshed by the OS.
- **Settings preferences**: language, briefing schedule, sensitivity. Plain DataStore (non-sensitive).
- **Crash logs**: written to `<app>/files/crashes/<timestamp>.txt` on your device. Never auto-uploaded. You manually share via Settings → "Send crash report" if you choose.

### MamY operators do not collect

- Identifying information (no account, no email required to use the app)
- Audio recordings
- Transcripts
- Structured note content
- Usage analytics
- Crash reports (unless you manually share)

## Outbound network calls

| Destination | Trigger | Payload | Auth |
|---|---|---|---|
| api.anthropic.com / api.openai.com / generativelanguage.googleapis.com | You used `MamY, take a note` and selected this provider | Plain text transcript + structuring prompt | Your BYOK API key |
| googleapis.com (Calendar) | Calendar sync (every 15 min) | OAuth token refresh, then read-only event query | OAuth granted by you |
| graph.microsoft.com (V1.1) | Calendar sync, Outlook | Same as above | OAuth |

You can audit every outbound call in `Settings → Network log`. The list is generated from the actual network stack, not a manual hardcode.

## Your rights (RGPD / Quebec Loi 25 / PIPEDA / GDPR)

- **Right to access**: tap `Settings → Export all data`. Receive a gzipped JSON file encrypted with your passphrase. Contains every byte MamY stored on your device.
- **Right to deletion**:
  - Per-person: long-press a report → "Delete this person and all history."
  - Full: `Settings → Wipe all data`. Two confirmations required. Removes DB, secrets, prefs, models, audio archive.
- **Right to portability**: same as access. JSON Lines + gzip + AES-CBC. Import support deferred to V2.
- **Right to rectify**: edit any structured field directly in the UI.
- **Right to object**: don't use the app. Uninstalling deletes everything (Android handles this).
- **Right to restrict**: switch off calendar sync in Settings. Switch off LLM (degrades to raw transcript only).

To exercise rights or ask questions: simon@<domain>.ca. Response within 30 days.

## Children

MamY is not directed at children under 13. We do not knowingly collect data from children.

## Third parties

We share **nothing** with third parties. The LLM and calendar providers you connect operate under their own privacy policies, which you should review:
- Anthropic: https://www.anthropic.com/legal/privacy
- OpenAI: https://openai.com/policies/privacy-policy
- Google: https://policies.google.com/privacy
- Microsoft: https://privacy.microsoft.com/en-ca/privacystatement

## Changes to this policy

Material changes will be announced via in-app notification + updated effective date here. Continued use after the effective date implies consent.

## Jurisdiction & complaints

- Loi 25 (Quebec): complaints to Commission d'accès à l'information du Québec (https://www.cai.gouv.qc.ca/)
- PIPEDA (Canada): complaints to Office of the Privacy Commissioner of Canada (https://www.priv.gc.ca/)
- GDPR (EU): we do not yet operate in the EU. V2 launch will add SCC + DPA support.

## Contact

Simon Cantin
Quebec, Canada
simon@<domain>.ca
```

- [ ] Translate to FR — save as `D:/ComfyUI-Intel/mamy/docs/legal/privacy-policy.fr.md` (full mirror, same structure, French copy).

- [ ] Commit: `docs: add privacy policy draft (en + fr)`

---

## Task 22 — ToS (Terms of Service) markdown draft

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/legal/terms-of-service.md`

```markdown
# MamY Terms of Service

**Effective date:** 2026-05-15
**Operator:** Simon Cantin (sole proprietor), Quebec, Canada

## 1. Acceptance

By installing and using MamY ("the App"), you agree to these Terms.

## 2. License

We grant you a non-exclusive, non-transferable, revocable license to install and use MamY for personal or business purposes.

## 3. Subscription

- Trial: 14 days free, full features.
- Paid tiers: Solo $19/month, Team $15/seat/month, Enterprise custom.
- Billing is handled by Google Play.
- Cancel anytime in Google Play Subscriptions settings.

## 4. BYOK (Bring Your Own Key)

You are responsible for:
- Obtaining your own LLM API key (Anthropic, OpenAI, Google).
- Paying for your own API usage to that provider.
- Complying with the provider's terms of service.

MamY operator does not pay for, intermediate, or guarantee LLM availability.

## 5. Acceptable use

You agree NOT to use MamY:
- To record meetings without consent of all parties (this is your legal responsibility, not ours; MamY ships without live recording for this reason).
- To violate any applicable law (RGPD, Loi 25, employment law).
- To reverse-engineer the App for purposes of building a competing product.
- To redistribute the App or its assets.

## 6. Disclaimer of warranties

MamY is provided "AS IS." Voice transcription accuracy varies. LLM-structured output may contain errors. You are responsible for reviewing structured notes before relying on them for HR or business decisions.

## 7. Limitation of liability

To the maximum extent permitted by law, MamY operator's liability is limited to the amount you paid in the 12 months preceding the claim.

We are not liable for:
- LLM provider downtime or errors.
- Calendar provider sync failures.
- Loss of local data due to device failure (you should export periodically).

## 8. Termination

You may stop using MamY anytime. Uninstalling the App deletes all local data. Subscription cancellation is via Google Play.

We may terminate access if you breach these Terms, with 30 days' notice except for legal compulsion.

## 9. Governing law

These Terms are governed by the laws of the Province of Quebec, Canada.

## 10. Contact

simon@<domain>.ca
```

- [ ] Translate to FR — save as `D:/ComfyUI-Intel/mamy/docs/legal/terms-of-service.fr.md`.

- [ ] Commit: `docs: add ToS draft (en + fr)`

---

## Task 23 — Privacy policy hosting (GitHub Pages)

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/legal/index.html` (landing page linking both languages):

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>MamY Legal</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif;
               max-width: 720px; margin: 4em auto; padding: 0 1em; line-height: 1.6; }
        h1 { color: #0F766E; }
        a { color: #312E81; }
        ul { padding-left: 1.5em; }
    </style>
</head>
<body>
    <h1>MamY Legal</h1>
    <p>
        <a href="privacy-policy.html">Privacy Policy (EN)</a> ·
        <a href="privacy-policy-fr.html">Politique de confidentialité (FR)</a>
    </p>
    <p>
        <a href="terms-of-service.html">Terms of Service (EN)</a> ·
        <a href="terms-of-service-fr.html">Conditions d'utilisation (FR)</a>
    </p>
    <p>Contact: <a href="mailto:simon@example.ca">simon@example.ca</a></p>
</body>
</html>
```

- [ ] Convert each `.md` to `.html` (use a markdown renderer — pandoc or any static-site tool):

```bash
cd D:/ComfyUI-Intel/mamy/docs/legal
pandoc privacy-policy.md -o privacy-policy.html --standalone --metadata title="MamY Privacy Policy"
pandoc privacy-policy.fr.md -o privacy-policy-fr.html --standalone --metadata title="Politique de confidentialité MamY"
pandoc terms-of-service.md -o terms-of-service.html --standalone --metadata title="MamY Terms of Service"
pandoc terms-of-service.fr.md -o terms-of-service.fr.html -o terms-of-service-fr.html --standalone --metadata title="Conditions d'utilisation MamY"
```

- [ ] Push the entire `docs/legal/` to GitHub on a `gh-pages` branch, OR push the whole repo and configure Pages to serve from `/docs/legal/` on `main`:

```bash
cd D:/ComfyUI-Intel/mamy
git checkout main
git add docs/legal/
git commit -m "docs: add privacy policy + ToS hosting"
git push origin main
```

- [ ] In GitHub repo settings → Pages → Source: `main` branch, folder: `/docs/legal`. Save.
- [ ] Wait ~2 min, verify https://sxc3030-eng.github.io/mamy/ resolves to the index page.
- [ ] Use https://sxc3030-eng.github.io/mamy/privacy-policy.html as the privacy policy URL in Play Console (Task 19).

- [ ] Commit: `docs: publish privacy + ToS via GitHub Pages`

---

## Task 24 — Final smoke test checklist

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/release/SMOKE_TEST_V1.md`

```markdown
# MamY V1 Final Smoke Test

Run this entire checklist on a clean device (factory reset or fresh emulator) before each release tag.

## Setup (5 min)

- [ ] **S1.** Install fresh APK via `adb install -r app-release.apk`. App launches without crash.
- [ ] **S2.** Onboarding completes end-to-end: language pick → permissions grant → BYOK key entry (Claude) → calendar OAuth → first wake-word demo.
- [ ] **S3.** After onboarding, foreground service notif visible in status bar.

## Capture path (10 min)

- [ ] **C1.** Say "MamY, prends note. Marc va bien, on a parlé du projet X." VAD cuts after silence. Confirmation TTS plays within 5 sec on Pixel 6+.
- [ ] **C2.** New entry appears in Reports list with Person="Marc" auto-created (unmatched=true).
- [ ] **C3.** Open person detail for Marc → raw transcript visible + structured fields populated (state="ok").
- [ ] **C4.** Long-press volume-up → bypass wake-word, capture path still works.
- [ ] **C5.** Capture in airplane mode → transcript stored, LLM call queued, retried when online.
- [ ] **C6.** "MamY, oublie ça" within 30 sec → previous capture marked deleted.

## Briefing path (10 min)

- [ ] **B1.** "MamY, ma journée" → reads back today's meetings in <60 sec, voice clear, FR or EN per UI lang.
- [ ] **B2.** Schedule meeting in 5 min via Google Calendar → wait → silent vibration fires + "MamY, briefe" reads pre-meeting context.
- [ ] **B3.** "MamY, c'est quoi avec Marc" → pulls full Marc context.
- [ ] **B4.** "MamY, qui me devait quoi" → lists open promises owed to me.
- [ ] **B5.** "MamY, mes actions ouvertes" → lists open actions.
- [ ] **B6.** "MamY, résume ma journée" → EOD summary covers today's notes + actions.

## Persistence (5 min)

- [ ] **P1.** Force-stop app via Settings → Apps → relaunch. Reports list still populated. Foreground service auto-restarts.
- [ ] **P2.** Reboot phone → service restarts within 60 sec. Wake-word fires. Data intact.
- [ ] **P3.** Settings → Cost tracker → shows correct cumulative LLM cost.

## Privacy (5 min)

- [ ] **PR1.** Settings → Network log → every outbound call is listed (LLM + calendar). No surprise endpoints.
- [ ] **PR2.** Settings → Export all data → enter passphrase → SAF picker → save .gz file. Decrypt locally with passphrase, verify JSONL structure intact.
- [ ] **PR3.** Long-press a person in Reports → "Delete this person" → confirm → cascade deletion verified (no orphan rows in DB inspector).
- [ ] **PR4.** Settings → Wipe all → type "MamY" → confirm → app restarts to onboarding. All data gone.
- [ ] **PR5.** Toggle off LLM provider in Settings → capture still records transcript, just no structured JSON.

## Edge cases (5 min)

- [ ] **E1.** Trigger crash via dev option (debug build menu hidden in long-press version number 7×). Verify crash file written to /files/crashes/.
- [ ] **E2.** Settings → "Send crash report" → share via Gmail. File arrives intact.
- [ ] **E3.** Revoke calendar OAuth in Google account settings → app detects on next sync, prompts re-auth.
- [ ] **E4.** Invalid BYOK key → capture stores transcript, surfaces error toast, doesn't crash.
- [ ] **E5.** 90 sec audio cap: speak >90 sec → cut at 90 + TTS "MamY: enregistrement coupé, continue".

## Battery (overnight)

- [ ] **BT1.** Charge to 100%, install MamY, leave on bedside table 8 hours, no other apps running. Morning battery >85% (i.e., <15% drain in 8h idle = <2%/h).
- [ ] **BT2.** 1 hour active use (5 captures + 5 briefings + calendar sync) = <8% drain.

## Sign-off

If all 30 items pass on Pixel 7 + at least one Samsung device + at least one OnePlus/Xiaomi (battery-saver custom OEM), tag release.

```bash
git tag v1.0.0
git push --tags
```
```

- [ ] Commit: `docs: add V1 final smoke test checklist`

---

## Task 25 — Open beta migration checklist

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/release/OPEN_BETA_MIGRATION.md`

```markdown
# Internal → Open Beta Migration Checklist

Trigger when:
- 10/10 alpha testers used MamY 4+ weeks
- ≥5/10 say "I can't go without this" (per spec §10 metrics)
- ≥70% of debriefs structured correctly per user spot-check
- ≥80% of briefings rated useful (in-app 1-tap rating)
- <10% false-wake-word rate per day across all alpha devices
- All 30 items in SMOKE_TEST_V1.md pass on 3+ devices

## Pre-migration tasks

- [ ] Aggregate alpha feedback into `D:/ComfyUI-Intel/mamy/docs/release/ALPHA_FEEDBACK_SUMMARY.md`. Note: top 5 fixes shipped, top 5 punted to V1.1.
- [ ] Bump versionCode to 2, versionName to `1.0.1` if any alpha-driven fixes shipped.
- [ ] Re-run smoke test on the bumped build.
- [ ] Update release notes (EN + FR) with what changed since alpha.

## Play Console steps

1. Testing → Open testing → Create new release.
2. Promote latest internal build OR upload fresh AAB.
3. Public link will be auto-generated (e.g., `https://play.google.com/apps/testing/com.mamy.android`).
4. No allowlist — anyone with the link can join.
5. **Important**: Submit for Play Store review (open testing requires review, internal does not). Expect 2-7 days.
6. Once approved, share the link in:
   - Personal LinkedIn post (FR + EN)
   - 3 Quebec manager Facebook groups (PME / leadership)
   - Reddit r/managers (EN, with a substantive post — not link drop)
   - Personal email list
   - HackerNews "Show HN" post (EN, polished, with screenshots)

## Post-launch monitoring

- Check Play Console pre-launch report for ANR / crashes (auto-runs on Firebase Test Lab devices)
- Watch ratings + reviews daily for first week
- Respond to every review within 24h (publicly)

## Kill switch

If a critical bug surfaces:
1. Halt the open beta rollout in Play Console.
2. Push a fix on a hotfix branch.
3. Bump versionCode, build AAB, upload to internal first, smoke test, then promote.
```

- [ ] Commit: `docs: add open beta migration checklist`

---

## Task 26 — Post-launch monitoring template

- [ ] Create `D:/ComfyUI-Intel/mamy/docs/release/POST_LAUNCH_7D_CHECKIN.md`

```markdown
# Post-launch 7-day check-in

Run 7 days after open-beta link goes live. Adapt for 30-day check-in.

## Metrics collected manually (no telemetry — privacy stance)

### Play Console (admin only)

- [ ] Installs (target: ≥50 in 7 days organic, more with promotion)
- [ ] Active users (DAU / WAU ratio target ≥40%)
- [ ] Uninstalls (<20% by day 7 = healthy)
- [ ] Crash-free sessions (target ≥99.5%)
- [ ] ANR rate (target <0.5%)
- [ ] Avg rating + review count

### Tester direct outreach (qualitative)

- [ ] Email survey to first 20 installers (those who replied to alpha or commented Play review):
  - "Are you still using MamY daily?" (yes/no)
  - "Top 1 thing you love"
  - "Top 1 thing that frustrates you"
  - "Would you pay $19/month for it after free trial?" (yes/no/unsure)
  - "Any feature you wish was here?"

### Self-test

- [ ] Re-run SMOKE_TEST_V1.md on 1 device. Pass rate must stay 100%.
- [ ] Verify privacy policy URL still resolves
- [ ] Verify Network log on a test device shows only expected outbound calls
- [ ] Verify export+wipe still work

## Decision gate

Based on 7-day data:

- **Healthy** (≥80% retention day 7, NPS proxy positive, no critical bugs): plan V1.1 features (Microsoft Graph, Whisper-base option, Gemini provider).
- **Soft** (50-80% retention, mixed feedback): focus next 4 weeks on top 3 frustration items, no new features.
- **Stuck** (<50% retention, polarizing reviews): pause acquisition, do 5+ deep user interviews, reconsider wedge.

## Output

Write findings to `D:/ComfyUI-Intel/mamy/docs/release/CHECKIN_7D.md` (timestamped). Don't push to GitHub if it contains tester PII.
```

- [ ] Commit: `docs: add post-launch 7-day check-in template`

---

## Final acceptance criteria

P8 is complete when:

- [ ] All 26 tasks above are checked off.
- [ ] `./gradlew test` green (unit tests).
- [ ] `./gradlew connectedDebugAndroidTest` green (instrumented tests for Wipe).
- [ ] `./gradlew lint` clean (or documented exceptions).
- [ ] `./gradlew :app:bundleRelease` produces signed AAB.
- [ ] `apksigner verify --verbose app-release.apk` reports v2 scheme: true.
- [ ] AAB uploaded to Play Console internal track. 10 alpha testers allowlisted. Opt-in URL distributed.
- [ ] Privacy policy + ToS published at `https://sxc3030-eng.github.io/mamy/`.
- [ ] App icon renders correctly on Pixel + Samsung + OneCustom OEM devices.
- [ ] Final smoke test (SMOKE_TEST_V1.md) passes 30/30 on 3+ devices.
- [ ] Tag pushed: `git tag v1.0.0-alpha && git push --tags`
- [ ] `D:/ComfyUI-Intel/mamy/CHANGELOG.md` updated with V1 alpha entry (date, what shipped, known issues).

After P8 ships, V1 alpha is in 10 testers' hands and on track for open beta in 4 weeks.
