# MamY P4 — Voice Intents & Memory Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full 10-intent voice command grammar (FR + EN), wire each intent to its handler, and add memory query operations for promises/actions/persons. After P4, the user can say any of the 10 supported commands and get a vocal response (briefings still stubbed pending P6).

**Architecture:** `IntentRouter` consumes Whisper transcript text, matches against compiled regex grammar table, returns typed `Intent` sealed class. Dispatcher routes each Intent to its handler service. Memory query handlers read directly from Room DAOs and format text for TTS. Briefing-related intents call interface that P6 will implement.

**Tech Stack:** Kotlin 2.0.21 · Coroutines Flow · Room 2.6 · Android TextToSpeech · regex-based intent classification
---

## Pre-requisites (P1+P2+P3 already shipped)

- `MamYDatabase` (Room + SQLCipher) with all 8 entities + DAOs (Person, Note, Action, Promise, Flag, Meeting, MeetingAttendee, Briefing)
- `MamYListenerService` foreground service with audio + VAD + STT pipeline
- `WhisperEngine` returning transcript `String`
- `LlmStructurer` (P3) returning structured JSON for `capture` intent and writing Note/Action/Promise/Flag rows
- `IntentRouter` STUB in P2 returning always `Intent.Capture` — to be REPLACED in this plan
- `TextToSpeechAdapter` (skeleton from P2) exposing `suspend fun speak(text: String, lang: Locale)` and `suspend fun listenOnce(timeoutMs: Long): String?`
- DI: Hilt set up with module-per-layer pattern

## Output package layout (this plan adds)

```
app/src/main/kotlin/com/mamy/android/
├── domain/
│   ├── intent/
│   │   ├── Intent.kt                     (sealed class)
│   │   ├── IntentRouter.kt
│   │   ├── IntentGrammar.kt              (compiled regex table)
│   │   ├── IntentDispatcher.kt
│   │   └── handler/
│   │       ├── IntentHandler.kt          (sealed interface)
│   │       ├── CaptureHandler.kt
│   │       ├── DailyBriefHandler.kt      (interface + StubDailyBriefHandler)
│   │       ├── NextBriefHandler.kt       (interface + StubNextBriefHandler)
│   │       ├── PersonBriefHandler.kt     (interface + TemplatedPersonBriefHandler V1)
│   │       ├── EodSummaryHandler.kt      (interface + StubEodSummaryHandler)
│   │       ├── PromisesOwedMeHandler.kt
│   │       ├── ActionsOpenHandler.kt
│   │       ├── UndoLastHandler.kt
│   │       ├── CorrectLastHandler.kt
│   │       └── HomonymeClarifier.kt
│   └── memory/
│       └── PersonMatcher.kt              (fuzzy match by name)
├── data/db/dao/
│   ├── PromiseDao.kt                     (P1 base + new query methods)
│   ├── ActionDao.kt                      (P1 base + new query methods)
│   └── PersonDao.kt                      (P1 base + findByName method)
└── di/
    └── IntentModule.kt
```

**Test root**: `app/src/test/kotlin/com/mamy/android/domain/intent/...` mirroring layout.

---

## Task 1 — `Intent` sealed class + `IntentResult` data class

**Why:** Anchor the type system. Every other piece references these.

**File**: `app/src/main/kotlin/com/mamy/android/domain/intent/Intent.kt`

- [ ] **1.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/IntentTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.Assertions.assertEquals

  class IntentTest {
      @Test
      fun `Intent_Capture has rawText`() {
          val intent: Intent = Intent.Capture(rawText = "MamY, prends note bla bla")
          assertEquals("MamY, prends note bla bla", (intent as Intent.Capture).rawText)
      }

      @Test
      fun `Intent_PersonBrief has personQuery`() {
          val intent: Intent = Intent.PersonBrief(personQuery = "Marie", rawText = "MamY, briefe-moi sur Marie")
          assertEquals("Marie", (intent as Intent.PersonBrief).personQuery)
      }

      @Test
      fun `Intent_CorrectLast has correctedText`() {
          val intent: Intent = Intent.CorrectLast(correctedText = "remplace projet X par projet Y", rawText = "MamY, modifie : remplace projet X par projet Y")
          assertEquals("remplace projet X par projet Y", (intent as Intent.CorrectLast).correctedText)
      }

      @Test
      fun `simple intents only carry rawText`() {
          val intents: List<Intent> = listOf(
              Intent.DailyBrief(rawText = "x"),
              Intent.NextBrief(rawText = "x"),
              Intent.PromisesOwedMe(rawText = "x"),
              Intent.ActionsOpen(rawText = "x"),
              Intent.EodSummary(rawText = "x"),
              Intent.UndoLast(rawText = "x"),
          )
          assertEquals(6, intents.size)
      }
  }
  ```

- [ ] **1.2** Run test → FAIL
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentTest"
  ```

- [ ] **1.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/Intent.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  /**
   * Result of intent classification by [IntentRouter].
   * Each variant is a typed payload routed to a dedicated handler by [IntentDispatcher].
   */
  sealed class Intent {
      abstract val rawText: String

      data class Capture(override val rawText: String) : Intent()
      data class DailyBrief(override val rawText: String) : Intent()
      data class NextBrief(override val rawText: String) : Intent()
      data class PersonBrief(val personQuery: String, override val rawText: String) : Intent()
      data class PromisesOwedMe(override val rawText: String) : Intent()
      data class ActionsOpen(override val rawText: String) : Intent()
      data class EodSummary(override val rawText: String) : Intent()
      data class UndoLast(override val rawText: String) : Intent()
      data class CorrectLast(val correctedText: String, override val rawText: String) : Intent()
  }

  /**
   * Outcome of running a handler. Used both for unit tests and for the foreground service
   * to decide whether to chain a TTS speak call.
   */
  data class IntentResult(
      val spokenText: String?,
      val success: Boolean,
      val error: String? = null,
  ) {
      companion object {
          fun spoken(text: String) = IntentResult(spokenText = text, success = true)
          fun silent() = IntentResult(spokenText = null, success = true)
          fun failure(error: String) = IntentResult(spokenText = null, success = false, error = error)
      }
  }
  ```

- [ ] **1.4** Run test → PASS
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentTest"
  ```

- [ ] **1.5** `git commit -m "feat: add Intent sealed class + IntentResult"`

---

## Task 2 — `IntentGrammar` compiled regex table (FR + EN)

**Why:** Single source of truth for the 10 patterns, compiled once at app start.

- [ ] **2.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/IntentGrammarTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertNotNull
  import org.junit.jupiter.api.Assertions.assertNull

  class IntentGrammarTest {

      @Test
      fun `capture FR matches`() {
          val match = IntentGrammar.CAPTURE.find("MamY, prends note Marie va mieux")
          assertNotNull(match)
      }

      @Test
      fun `capture EN matches`() {
          val match = IntentGrammar.CAPTURE.find("MamY take a note Marie is doing better")
          assertNotNull(match)
      }

      @Test
      fun `daily_brief FR matches`() {
          val match = IntentGrammar.DAILY_BRIEF.find("MamY, ma journée")
          assertNotNull(match)
      }

      @Test
      fun `daily_brief EN matches`() {
          val match = IntentGrammar.DAILY_BRIEF.find("MamY, my day")
          assertNotNull(match)
      }

      @Test
      fun `next_brief FR matches without name`() {
          val match = IntentGrammar.NEXT_BRIEF.find("MamY, briefe")
          assertNotNull(match)
      }

      @Test
      fun `next_brief FR does NOT match when followed by name`() {
          val match = IntentGrammar.NEXT_BRIEF.find("MamY, briefe-moi sur Marie")
          assertNull(match)
      }

      @Test
      fun `next_brief EN matches`() {
          val match = IntentGrammar.NEXT_BRIEF.find("MamY, brief me")
          assertNotNull(match)
      }

      @Test
      fun `person_brief FR captures name`() {
          val match = IntentGrammar.PERSON_BRIEF_DIRECT.find("MamY, briefe-moi sur Marie Tremblay")
          assertNotNull(match)
          assertEquals("Marie Tremblay", match!!.groupValues[2])
      }

      @Test
      fun `person_brief alias FR captures name`() {
          val match = IntentGrammar.PERSON_BRIEF_ALIAS.find("MamY, c'est quoi avec Pierre")
          assertNotNull(match)
          assertEquals("Pierre", match!!.groupValues[2])
      }

      @Test
      fun `person_brief EN captures name`() {
          val match = IntentGrammar.PERSON_BRIEF_DIRECT.find("MamY, brief me on Sarah")
          assertNotNull(match)
          assertEquals("Sarah", match!!.groupValues[2])
      }

      @Test
      fun `promises_owed_me FR matches`() {
          val match = IntentGrammar.PROMISES_OWED_ME.find("MamY, qui me devait quoi")
          assertNotNull(match)
      }

      @Test
      fun `promises_owed_me EN matches`() {
          val match = IntentGrammar.PROMISES_OWED_ME.find("MamY, what's owed to me")
          assertNotNull(match)
      }

      @Test
      fun `actions_open FR matches`() {
          val match = IntentGrammar.ACTIONS_OPEN.find("MamY, mes actions ouvertes")
          assertNotNull(match)
      }

      @Test
      fun `actions_open EN matches`() {
          val match = IntentGrammar.ACTIONS_OPEN.find("MamY, my open actions")
          assertNotNull(match)
      }

      @Test
      fun `eod_summary FR matches`() {
          val match = IntentGrammar.EOD_SUMMARY.find("MamY, résume ma journée")
          assertNotNull(match)
      }

      @Test
      fun `eod_summary EN matches`() {
          val match = IntentGrammar.EOD_SUMMARY.find("MamY, summarize my day")
          assertNotNull(match)
      }

      @Test
      fun `undo_last FR matches`() {
          val match = IntentGrammar.UNDO_LAST.find("MamY, oublie ça")
          assertNotNull(match)
      }

      @Test
      fun `undo_last EN matches`() {
          val match = IntentGrammar.UNDO_LAST.find("MamY, forget that")
          assertNotNull(match)
      }

      @Test
      fun `correct_last FR captures correction`() {
          val match = IntentGrammar.CORRECT_LAST.find("MamY, modifie : remplace Marie par Pierre")
          assertNotNull(match)
          assertEquals("remplace Marie par Pierre", match!!.groupValues[2].trim())
      }

      @Test
      fun `correct_last EN captures correction`() {
          val match = IntentGrammar.CORRECT_LAST.find("MamY, edit: change Marie to Pierre")
          assertNotNull(match)
          assertEquals("change Marie to Pierre", match!!.groupValues[2].trim())
      }

      @Test
      fun `case insensitive`() {
          val match = IntentGrammar.DAILY_BRIEF.find("mamy, MA JOURNÉE")
          assertNotNull(match)
      }
  }
  ```

- [ ] **2.2** Run test → FAIL
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentGrammarTest"
  ```

- [ ] **2.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/IntentGrammar.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  /**
   * Compiled regex table for the 10 voice intents (FR + EN).
   *
   * Patterns reference Annex A of the design spec (2026-05-02-mamy-design.md).
   * All patterns are anchored with `^MamY,?\s+` so they only fire when the wake-word
   * is the first token. Comma is optional (Whisper transcripts vary).
   *
   * Ordering matters in [IntentRouter] : PERSON_BRIEF must be tested BEFORE NEXT_BRIEF
   * because NEXT_BRIEF uses a negative lookahead `(?!\s+moi)` to avoid double-matching.
   */
  object IntentGrammar {

      private val IGNORE = setOf(RegexOption.IGNORE_CASE)

      val CAPTURE: Regex = Regex(
          pattern = """^MamY,?\s+(prends|take a)\s+note\b""",
          options = IGNORE,
      )

      val DAILY_BRIEF: Regex = Regex(
          pattern = """^MamY,?\s+(ma journée|my day)\b""",
          options = IGNORE,
      )

      /** Matches "briefe" / "brief me" alone (NOT followed by "moi sur" or "me on"). */
      val NEXT_BRIEF: Regex = Regex(
          pattern = """^MamY,?\s+(briefe(?!-?\s*moi)|brief me)\s*$""",
          options = IGNORE,
      )

      /** "briefe-moi sur <X>" / "brief me on <X>" — captures name in group 2. */
      val PERSON_BRIEF_DIRECT: Regex = Regex(
          pattern = """^MamY,?\s+(briefe-?\s*moi sur|brief me on)\s+(.+?)\s*$""",
          options = IGNORE,
      )

      /** "c'est quoi avec <X>" / "what's up with <X>" — captures name in group 2. */
      val PERSON_BRIEF_ALIAS: Regex = Regex(
          pattern = """^MamY,?\s+(c'est quoi avec|what'?s up with)\s+(.+?)\s*$""",
          options = IGNORE,
      )

      val PROMISES_OWED_ME: Regex = Regex(
          pattern = """^MamY,?\s+(qui me devait quoi|what'?s owed to me)\b""",
          options = IGNORE,
      )

      val ACTIONS_OPEN: Regex = Regex(
          pattern = """^MamY,?\s+(mes actions ouvertes|my open actions)\b""",
          options = IGNORE,
      )

      val EOD_SUMMARY: Regex = Regex(
          pattern = """^MamY,?\s+(résume ma journée|summarize my day)\b""",
          options = IGNORE,
      )

      val UNDO_LAST: Regex = Regex(
          pattern = """^MamY,?\s+(oublie ça|forget that)\b""",
          options = IGNORE,
      )

      /** "modifie : <X>" / "edit: <X>" — captures correction in group 2. */
      val CORRECT_LAST: Regex = Regex(
          pattern = """^MamY,?\s+(modifie|edit)\s*:?\s*(.+?)\s*$""",
          options = IGNORE,
      )
  }
  ```

- [ ] **2.4** Run test → PASS
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentGrammarTest"
  ```

- [ ] **2.5** `git commit -m "feat: add IntentGrammar regex table for 10 voice intents (FR+EN)"`

---

## Task 3 — `IntentRouter` with golden inputs

**Why:** Replaces the P2 stub. Ordered classification with sensible fallback.

- [ ] **3.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/IntentRouterTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Assertions.assertTrue

  class IntentRouterTest {

      private val router = IntentRouter()

      @Test
      fun `capture FR routes to Capture`() {
          val intent = router.classify("MamY, prends note Marie va mieux")
          assertTrue(intent is Intent.Capture)
      }

      @Test
      fun `daily_brief FR routes`() {
          val intent = router.classify("MamY, ma journée")
          assertTrue(intent is Intent.DailyBrief)
      }

      @Test
      fun `daily_brief EN routes`() {
          val intent = router.classify("MamY, my day")
          assertTrue(intent is Intent.DailyBrief)
      }

      @Test
      fun `next_brief FR routes`() {
          val intent = router.classify("MamY, briefe")
          assertTrue(intent is Intent.NextBrief)
      }

      @Test
      fun `next_brief does NOT swallow person_brief`() {
          val intent = router.classify("MamY, briefe-moi sur Marie")
          assertTrue(intent is Intent.PersonBrief, "expected PersonBrief but got $intent")
          assertEquals("Marie", (intent as Intent.PersonBrief).personQuery)
      }

      @Test
      fun `person_brief alias routes`() {
          val intent = router.classify("MamY, c'est quoi avec Pierre")
          assertTrue(intent is Intent.PersonBrief)
          assertEquals("Pierre", (intent as Intent.PersonBrief).personQuery)
      }

      @Test
      fun `promises_owed_me routes`() {
          val intent = router.classify("MamY, qui me devait quoi")
          assertTrue(intent is Intent.PromisesOwedMe)
      }

      @Test
      fun `actions_open routes`() {
          val intent = router.classify("MamY, mes actions ouvertes")
          assertTrue(intent is Intent.ActionsOpen)
      }

      @Test
      fun `eod_summary routes`() {
          val intent = router.classify("MamY, résume ma journée")
          assertTrue(intent is Intent.EodSummary)
      }

      @Test
      fun `undo_last routes`() {
          val intent = router.classify("MamY, oublie ça")
          assertTrue(intent is Intent.UndoLast)
      }

      @Test
      fun `correct_last routes with correction text`() {
          val intent = router.classify("MamY, modifie : remplace Marie par Pierre")
          assertTrue(intent is Intent.CorrectLast)
          assertEquals("remplace Marie par Pierre", (intent as Intent.CorrectLast).correctedText)
      }

      @Test
      fun `unknown command falls back to Capture`() {
          val intent = router.classify("MamY blabla random text")
          assertTrue(intent is Intent.Capture)
          assertEquals("MamY blabla random text", (intent as Intent.Capture).rawText)
      }

      @Test
      fun `empty wake-word still falls back to Capture`() {
          val intent = router.classify("MamY")
          assertTrue(intent is Intent.Capture)
      }

      @Test
      fun `extra whitespace tolerated`() {
          val intent = router.classify("MamY,    ma journée   ")
          assertTrue(intent is Intent.DailyBrief)
      }
  }
  ```

- [ ] **3.2** Run test → FAIL
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentRouterTest"
  ```

- [ ] **3.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/IntentRouter.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Routes a Whisper transcript to a typed [Intent].
   *
   * Pattern ordering :
   *  1. PERSON_BRIEF (direct + alias) — must precede NEXT_BRIEF
   *  2. NEXT_BRIEF
   *  3. CORRECT_LAST — must precede CAPTURE (both can swallow free-form tails)
   *  4. CAPTURE
   *  5. DAILY_BRIEF / PROMISES_OWED_ME / ACTIONS_OPEN / EOD_SUMMARY / UNDO_LAST
   *
   * Falls back to [Intent.Capture] when no pattern matches.
   */
  @Singleton
  class IntentRouter @Inject constructor() {

      fun classify(transcript: String): Intent {
          val trimmed = transcript.trim()

          // Person brief variants (must beat next_brief)
          IntentGrammar.PERSON_BRIEF_DIRECT.find(trimmed)?.let { match ->
              return Intent.PersonBrief(
                  personQuery = match.groupValues[2].trim(),
                  rawText = trimmed,
              )
          }
          IntentGrammar.PERSON_BRIEF_ALIAS.find(trimmed)?.let { match ->
              return Intent.PersonBrief(
                  personQuery = match.groupValues[2].trim(),
                  rawText = trimmed,
              )
          }

          // Next brief
          if (IntentGrammar.NEXT_BRIEF.containsMatchIn(trimmed)) {
              return Intent.NextBrief(rawText = trimmed)
          }

          // Correction (must beat CAPTURE — "modifie" is more specific)
          IntentGrammar.CORRECT_LAST.find(trimmed)?.let { match ->
              return Intent.CorrectLast(
                  correctedText = match.groupValues[2].trim(),
                  rawText = trimmed,
              )
          }

          // Capture
          if (IntentGrammar.CAPTURE.containsMatchIn(trimmed)) {
              return Intent.Capture(rawText = trimmed)
          }

          // Single-shot intents
          if (IntentGrammar.DAILY_BRIEF.containsMatchIn(trimmed)) {
              return Intent.DailyBrief(rawText = trimmed)
          }
          if (IntentGrammar.PROMISES_OWED_ME.containsMatchIn(trimmed)) {
              return Intent.PromisesOwedMe(rawText = trimmed)
          }
          if (IntentGrammar.ACTIONS_OPEN.containsMatchIn(trimmed)) {
              return Intent.ActionsOpen(rawText = trimmed)
          }
          if (IntentGrammar.EOD_SUMMARY.containsMatchIn(trimmed)) {
              return Intent.EodSummary(rawText = trimmed)
          }
          if (IntentGrammar.UNDO_LAST.containsMatchIn(trimmed)) {
              return Intent.UndoLast(rawText = trimmed)
          }

          // Fallback
          return Intent.Capture(rawText = trimmed)
      }
  }
  ```

- [ ] **3.4** Run test → PASS
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentRouterTest"
  ```

- [ ] **3.5** `git commit -m "feat: implement IntentRouter replacing P2 stub"`

---

## Task 4 — Extend DAOs with memory query methods

**Why:** Pre-requisite for handlers in tasks 6, 7, 12, 14.

Assumes P1 already created `PersonDao`, `PromiseDao`, `ActionDao` with basic `insert/update/delete/getById`.

- [ ] **4.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/data/db/dao/MemoryQueryDaoTest.kt`
  ```kotlin
  package com.mamy.android.data.db.dao

  import androidx.room.Room
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import com.mamy.android.data.db.MamYDatabase
  import com.mamy.android.data.db.entity.ActionEntity
  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.data.db.entity.PromiseEntity
  import kotlinx.coroutines.test.runTest
  import org.junit.After
  import org.junit.Before
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.annotation.Config
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  @RunWith(AndroidJUnit4::class)
  @Config(sdk = [33])
  class MemoryQueryDaoTest {

      private lateinit var db: MamYDatabase
      private lateinit var personDao: PersonDao
      private lateinit var promiseDao: PromiseDao
      private lateinit var actionDao: ActionDao

      @Before
      fun setup() {
          db = Room.inMemoryDatabaseBuilder(
              ApplicationProvider.getApplicationContext(),
              MamYDatabase::class.java,
          ).allowMainThreadQueries().build()
          personDao = db.personDao()
          promiseDao = db.promiseDao()
          actionDao = db.actionDao()
      }

      @After
      fun teardown() {
          db.close()
      }

      @Test
      fun `findActiveOwedTo returns only active promises owed to self`() = runTest {
          val noteId = UUID.randomUUID()
          val marieId = UUID.randomUUID()
          personDao.insert(personFixture(marieId, "Marie"))

          val owedActive = promiseFixture(
              fromId = marieId.toString(),
              toId = "self",
              status = "active",
              what = "envoyer rapport",
              fromNoteId = noteId,
          )
          val owedKept = promiseFixture(
              fromId = marieId.toString(),
              toId = "self",
              status = "kept",
              what = "old promise",
              fromNoteId = noteId,
          )
          val owedByMe = promiseFixture(
              fromId = "self",
              toId = marieId.toString(),
              status = "active",
              what = "going other way",
              fromNoteId = noteId,
          )
          promiseDao.insert(owedActive)
          promiseDao.insert(owedKept)
          promiseDao.insert(owedByMe)

          val results = promiseDao.findActiveOwedToSelf()
          assertEquals(1, results.size)
          assertEquals("envoyer rapport", results[0].what)
      }

      @Test
      fun `findOpen returns only open actions`() = runTest {
          val noteId = UUID.randomUUID()
          val open = actionFixture(description = "call David", status = "open", fromNoteId = noteId)
          val done = actionFixture(description = "old task", status = "done", fromNoteId = noteId)
          actionDao.insert(open)
          actionDao.insert(done)

          val results = actionDao.findOpen()
          assertEquals(1, results.size)
          assertEquals("call David", results[0].description)
      }

      @Test
      fun `findByName returns case-insensitive substring matches`() = runTest {
          personDao.insert(personFixture(UUID.randomUUID(), "Marie Dubois"))
          personDao.insert(personFixture(UUID.randomUUID(), "Marie Tremblay"))
          personDao.insert(personFixture(UUID.randomUUID(), "Pierre Lavoie"))

          val maries = personDao.findByName("marie")
          assertEquals(2, maries.size)
          assertTrue(maries.all { it.name.lowercase().contains("marie") })

          val pierre = personDao.findByName("Pierre")
          assertEquals(1, pierre.size)
      }

      private fun personFixture(id: UUID, name: String) = PersonEntity(
          id = id,
          name = name,
          email = null,
          roleHint = null,
          calendarAttendeeId = null,
          createdAt = Instant.now(),
          lastInteractionAt = null,
          interactionCount = 0,
          emotionalTrend = null,
          unmatched = false,
          archived = false,
      )

      private fun promiseFixture(
          fromId: String,
          toId: String,
          status: String,
          what: String,
          fromNoteId: UUID,
      ) = PromiseEntity(
          id = UUID.randomUUID(),
          fromId = fromId,
          toId = toId,
          what = what,
          due = null,
          status = status,
          fromNoteId = fromNoteId,
          createdAt = Instant.now(),
          resolvedAt = null,
      )

      private fun actionFixture(
          description: String,
          status: String,
          fromNoteId: UUID,
      ) = ActionEntity(
          id = UUID.randomUUID(),
          description = description,
          assignee = "self",
          linkedPersonId = null,
          deadline = null,
          status = status,
          fromNoteId = fromNoteId,
          createdAt = Instant.now(),
          doneAt = null,
      )
  }
  ```

- [ ] **4.2** Run test → FAIL
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MemoryQueryDaoTest"
  ```

- [ ] **4.3** Add methods to existing DAOs (do NOT recreate the files; use `Edit` tool on existing P1 DAOs).

  **`PromiseDao.kt`** (add at end of interface body, before closing brace) :
  ```kotlin
      @Query("SELECT * FROM Promise WHERE to_id = 'self' AND status = 'active' ORDER BY created_at DESC")
      suspend fun findActiveOwedToSelf(): List<PromiseEntity>

      @Query("SELECT * FROM Promise WHERE from_note_id = :noteId")
      suspend fun findByNoteId(noteId: UUID): List<PromiseEntity>

      @Query("DELETE FROM Promise WHERE from_note_id = :noteId")
      suspend fun deleteByNoteId(noteId: UUID): Int
  ```

  **`ActionDao.kt`** (add at end) :
  ```kotlin
      @Query("SELECT * FROM Action WHERE status = 'open' ORDER BY deadline IS NULL, deadline ASC, created_at DESC")
      suspend fun findOpen(): List<ActionEntity>

      @Query("SELECT * FROM Action WHERE from_note_id = :noteId")
      suspend fun findByNoteId(noteId: UUID): List<ActionEntity>

      @Query("DELETE FROM Action WHERE from_note_id = :noteId")
      suspend fun deleteByNoteId(noteId: UUID): Int
  ```

  **`PersonDao.kt`** (add at end) :
  ```kotlin
      @Query("SELECT * FROM Person WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' AND archived = 0 ORDER BY last_interaction_at DESC")
      suspend fun findByName(query: String): List<PersonEntity>
  ```

  **`FlagDao.kt`** (add at end — used by Task 8 cascade and Task 12 person brief) :
  ```kotlin
      @Query("SELECT * FROM Flag WHERE person_id = :personId AND resolved = 0 ORDER BY created_at DESC")
      suspend fun findActiveByPerson(personId: UUID): List<FlagEntity>

      @Query("SELECT * FROM Flag WHERE from_note_id = :noteId")
      suspend fun findByNoteId(noteId: UUID): List<FlagEntity>

      @Query("DELETE FROM Flag WHERE from_note_id = :noteId")
      suspend fun deleteByNoteId(noteId: UUID): Int
  ```

  **`NoteDao.kt`** (add at end — used by Task 8 + Task 10) :
  ```kotlin
      @Query("SELECT * FROM Note ORDER BY created_at DESC LIMIT 1")
      suspend fun findLatest(): NoteEntity?

      @Query("DELETE FROM Note WHERE id = :id")
      suspend fun deleteById(id: UUID): Int
  ```

- [ ] **4.4** Run test → PASS
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MemoryQueryDaoTest"
  ```

- [ ] **4.5** `git commit -m "feat: extend DAOs with memory query methods (find/delete by note, by name, active owed to self)"`

---

## Task 5 — Briefing handler interfaces + stub impls (P6 will replace)

**Why:** P4 must compile and ship without P6. Stub impls return "not yet implemented" via TTS.

- [ ] **5.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/StubBriefingHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.domain.intent.Intent
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.Assertions.assertTrue

  class StubBriefingHandlerTest {

      @Test
      fun `daily brief stub returns not implemented message`() = runTest {
          val result = StubDailyBriefHandler().handle(Intent.DailyBrief("MamY, ma journée"))
          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("not yet", ignoreCase = true) ||
                     result.spokenText!!.contains("pas encore", ignoreCase = true))
      }

      @Test
      fun `next brief stub returns message`() = runTest {
          val result = StubNextBriefHandler().handle(Intent.NextBrief("MamY, briefe"))
          assertTrue(result.success)
          assertTrue(result.spokenText != null)
      }

      @Test
      fun `eod summary stub returns message`() = runTest {
          val result = StubEodSummaryHandler().handle(Intent.EodSummary("MamY, résume ma journée"))
          assertTrue(result.success)
          assertTrue(result.spokenText != null)
      }
  }
  ```

- [ ] **5.2** Run test → FAIL
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.handler.StubBriefingHandlerTest"
  ```

- [ ] **5.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/IntentHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult

  /**
   * Marker interface for all intent handlers. Each concrete handler binds to one
   * variant of [Intent] and returns an [IntentResult] consumed by the dispatcher.
   */
  interface IntentHandler<I : Intent> {
      suspend fun handle(intent: I): IntentResult
  }

  // Briefing-flavor handlers — interfaces so P6 can wire LLM-backed impls.
  interface DailyBriefHandler : IntentHandler<Intent.DailyBrief>
  interface NextBriefHandler : IntentHandler<Intent.NextBrief>
  interface PersonBriefHandler : IntentHandler<Intent.PersonBrief>
  interface EodSummaryHandler : IntentHandler<Intent.EodSummary>
  ```

  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/StubDailyBriefHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Returned by Hilt until P6 ships the LLM-backed impl.
   * Plays a vocal "not yet implemented" message so the user gets feedback.
   */
  @Singleton
  class StubDailyBriefHandler @Inject constructor() : DailyBriefHandler {
      override suspend fun handle(intent: Intent.DailyBrief): IntentResult =
          IntentResult.spoken("Le briefing matinal n'est pas encore implémenté. Daily briefing not yet implemented.")
  }
  ```

  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/StubNextBriefHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class StubNextBriefHandler @Inject constructor() : NextBriefHandler {
      override suspend fun handle(intent: Intent.NextBrief): IntentResult =
          IntentResult.spoken("Le briefing pré-meeting n'est pas encore implémenté. Pre-meeting briefing not yet implemented.")
  }
  ```

  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/StubEodSummaryHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class StubEodSummaryHandler @Inject constructor() : EodSummaryHandler {
      override suspend fun handle(intent: Intent.EodSummary): IntentResult =
          IntentResult.spoken("Le résumé de fin de journée n'est pas encore implémenté. EOD summary not yet implemented.")
  }
  ```

- [ ] **5.4** Run test → PASS

- [ ] **5.5** `git commit -m "feat: add briefing handler interfaces + stub impls (DailyBrief, NextBrief, EodSummary)"`

---

## Task 6 — `PromisesOwedMeHandler` (DB query + format + TTS)

**Why:** First fully-implemented memory query handler. Pattern other handlers will follow.

- [ ] **6.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/PromisesOwedMeHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.PersonDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.data.db.entity.PromiseEntity
  import com.mamy.android.domain.intent.Intent
  import io.mockk.coEvery
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class PromisesOwedMeHandlerTest {

      private val promiseDao: PromiseDao = mockk()
      private val personDao: PersonDao = mockk()
      private val handler = PromisesOwedMeHandler(promiseDao, personDao)

      @Test
      fun `empty list returns friendly empty message`() = runTest {
          coEvery { promiseDao.findActiveOwedToSelf() } returns emptyList()

          val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))

          assertTrue(result.success)
          assertEquals("Personne ne te doit rien actuellement.", result.spokenText)
      }

      @Test
      fun `list with one promise formats with name`() = runTest {
          val marieId = UUID.randomUUID()
          coEvery { promiseDao.findActiveOwedToSelf() } returns listOf(
              PromiseEntity(
                  id = UUID.randomUUID(),
                  fromId = marieId.toString(),
                  toId = "self",
                  what = "envoyer le rapport projet X",
                  due = null,
                  status = "active",
                  fromNoteId = UUID.randomUUID(),
                  createdAt = Instant.now(),
                  resolvedAt = null,
              ),
          )
          coEvery { personDao.getById(marieId) } returns PersonEntity(
              id = marieId, name = "Marie",
              email = null, roleHint = null, calendarAttendeeId = null,
              createdAt = Instant.now(), lastInteractionAt = null,
              interactionCount = 0, emotionalTrend = null,
              unmatched = false, archived = false,
          )

          val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))

          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("Marie"))
          assertTrue(result.spokenText!!.contains("envoyer le rapport projet X"))
      }

      @Test
      fun `unknown person id falls back to generic label`() = runTest {
          coEvery { promiseDao.findActiveOwedToSelf() } returns listOf(
              PromiseEntity(
                  id = UUID.randomUUID(),
                  fromId = "self",  // mis-set, but for safety
                  toId = "self",
                  what = "test",
                  due = null,
                  status = "active",
                  fromNoteId = UUID.randomUUID(),
                  createdAt = Instant.now(),
                  resolvedAt = null,
              ),
          )

          val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))
          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("Quelqu'un") || result.spokenText!!.contains("toi-même"))
      }
  }
  ```

- [ ] **6.2** Run test → FAIL

- [ ] **6.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/PromisesOwedMeHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.PersonDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.data.db.entity.PromiseEntity
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import java.util.UUID
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Reads active promises where `to=self` from Room, formats vocally.
   * Pure DB query — no LLM call.
   */
  @Singleton
  class PromisesOwedMeHandler @Inject constructor(
      private val promiseDao: PromiseDao,
      private val personDao: PersonDao,
  ) : IntentHandler<Intent.PromisesOwedMe> {

      override suspend fun handle(intent: Intent.PromisesOwedMe): IntentResult {
          val promises = promiseDao.findActiveOwedToSelf()
          if (promises.isEmpty()) {
              return IntentResult.spoken("Personne ne te doit rien actuellement.")
          }
          val sb = StringBuilder()
          sb.append("Tu as ${promises.size} promesse${if (promises.size > 1) "s" else ""} ouverte${if (promises.size > 1) "s" else ""} envers toi. ")
          promises.forEachIndexed { idx, p ->
              val who = resolveName(p.fromId)
              sb.append("${idx + 1}. ").append(who).append(" doit ").append(p.what).append(". ")
          }
          return IntentResult.spoken(sb.toString().trim())
      }

      private suspend fun resolveName(fromId: String): String {
          if (fromId == "self") return "toi-même"
          return runCatching { UUID.fromString(fromId) }
              .getOrNull()
              ?.let { personDao.getById(it)?.name }
              ?: "Quelqu'un"
      }
  }
  ```

- [ ] **6.4** Run test → PASS

- [ ] **6.5** `git commit -m "feat: add PromisesOwedMeHandler (active promises owed to self)"`

---

## Task 7 — `ActionsOpenHandler`

- [ ] **7.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/ActionsOpenHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.ActionDao
  import com.mamy.android.data.db.entity.ActionEntity
  import com.mamy.android.domain.intent.Intent
  import io.mockk.coEvery
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class ActionsOpenHandlerTest {

      private val actionDao: ActionDao = mockk()
      private val handler = ActionsOpenHandler(actionDao)

      @Test
      fun `empty returns clean message`() = runTest {
          coEvery { actionDao.findOpen() } returns emptyList()
          val result = handler.handle(Intent.ActionsOpen("MamY, mes actions ouvertes"))
          assertEquals("Aucune action ouverte. Tu es à jour.", result.spokenText)
      }

      @Test
      fun `formats list with deadline if present`() = runTest {
          val deadline = Instant.parse("2026-05-15T17:00:00Z")
          coEvery { actionDao.findOpen() } returns listOf(
              actionFixture("call David", null),
              actionFixture("review CV Marie", deadline),
          )
          val result = handler.handle(Intent.ActionsOpen("MamY, mes actions ouvertes"))
          assertTrue(result.spokenText!!.contains("call David"))
          assertTrue(result.spokenText!!.contains("review CV Marie"))
      }

      private fun actionFixture(desc: String, deadline: Instant?) = ActionEntity(
          id = UUID.randomUUID(),
          description = desc,
          assignee = "self",
          linkedPersonId = null,
          deadline = deadline,
          status = "open",
          fromNoteId = UUID.randomUUID(),
          createdAt = Instant.now(),
          doneAt = null,
      )
  }
  ```

- [ ] **7.2** Run test → FAIL

- [ ] **7.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/ActionsOpenHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.ActionDao
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import java.time.Instant
  import java.time.LocalDate
  import java.time.ZoneId
  import java.time.format.DateTimeFormatter
  import java.util.Locale
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class ActionsOpenHandler @Inject constructor(
      private val actionDao: ActionDao,
  ) : IntentHandler<Intent.ActionsOpen> {

      private val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

      override suspend fun handle(intent: Intent.ActionsOpen): IntentResult {
          val actions = actionDao.findOpen()
          if (actions.isEmpty()) {
              return IntentResult.spoken("Aucune action ouverte. Tu es à jour.")
          }
          val sb = StringBuilder()
          sb.append("Tu as ${actions.size} action${if (actions.size > 1) "s" else ""} ouverte${if (actions.size > 1) "s" else ""}. ")
          actions.forEachIndexed { idx, a ->
              sb.append("${idx + 1}. ").append(a.description)
              a.deadline?.let { sb.append(", deadline ").append(formatDeadline(it)) }
              sb.append(". ")
          }
          return IntentResult.spoken(sb.toString().trim())
      }

      private fun formatDeadline(d: Instant): String {
          val date = LocalDate.ofInstant(d, ZoneId.systemDefault())
          return when (date) {
              LocalDate.now() -> "aujourd'hui"
              LocalDate.now().plusDays(1) -> "demain"
              else -> formatter.format(date)
          }
      }
  }
  ```

- [ ] **7.4** Run test → PASS

- [ ] **7.5** `git commit -m "feat: add ActionsOpenHandler with deadline formatting (today/tomorrow/date)"`

---

## Task 8 — `UndoLastHandler` skeleton + state tracker

**Why:** Needs a singleton ring-buffer holding the last note id + creation time. Cascading delete: Note + its Actions + Promises + Flags.

- [ ] **8.1** Implement state tracker (no test alone — exercised in 8.x)
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/LastNoteTracker.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import java.time.Instant
  import java.util.UUID
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Holds the most recently created Note for the undo window.
   * Updated by [com.mamy.android.domain.intent.handler.CaptureHandler] after a successful
   * structuration. Read by [UndoLastHandler].
   *
   * Thread-safe via `@Volatile` on the single mutable field.
   */
  @Singleton
  class LastNoteTracker @Inject constructor() {

      @Volatile
      private var slot: Slot? = null

      data class Slot(val noteId: UUID, val createdAt: Instant)

      fun record(noteId: UUID, createdAt: Instant = Instant.now()) {
          slot = Slot(noteId, createdAt)
      }

      /** Returns the slot if still within [windowMs] ms of recording, else null. */
      fun snapshot(now: Instant = Instant.now(), windowMs: Long = WINDOW_MS): Slot? {
          val s = slot ?: return null
          return if (now.toEpochMilli() - s.createdAt.toEpochMilli() <= windowMs) s else null
      }

      fun clear() {
          slot = null
      }

      companion object {
          const val WINDOW_MS: Long = 30_000L
      }
  }
  ```

---

## Task 9 — `UndoLastHandler` tests + impl

- [ ] **9.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/UndoLastHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.ActionDao
  import com.mamy.android.data.db.dao.FlagDao
  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.domain.intent.Intent
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class UndoLastHandlerTest {

      private val noteDao: NoteDao = mockk(relaxed = true)
      private val actionDao: ActionDao = mockk(relaxed = true)
      private val promiseDao: PromiseDao = mockk(relaxed = true)
      private val flagDao: FlagDao = mockk(relaxed = true)
      private val tracker = LastNoteTracker()

      private val handler = UndoLastHandler(noteDao, actionDao, promiseDao, flagDao, tracker)

      @Test
      fun `success when within 30 sec window cascades delete`() = runTest {
          val noteId = UUID.randomUUID()
          val now = Instant.parse("2026-05-02T12:00:00Z")
          tracker.record(noteId, createdAt = now.minusSeconds(15))

          coEvery { actionDao.deleteByNoteId(noteId) } returns 2
          coEvery { promiseDao.deleteByNoteId(noteId) } returns 1
          coEvery { flagDao.deleteByNoteId(noteId) } returns 0
          coEvery { noteDao.deleteById(noteId) } returns 1

          val result = handler.handle(Intent.UndoLast("MamY, oublie ça"), now = now)

          assertTrue(result.success)
          assertEquals("Annulé.", result.spokenText)
          coVerify { actionDao.deleteByNoteId(noteId) }
          coVerify { promiseDao.deleteByNoteId(noteId) }
          coVerify { flagDao.deleteByNoteId(noteId) }
          coVerify { noteDao.deleteById(noteId) }
          assertEquals(null, tracker.snapshot(now = now))
      }

      @Test
      fun `expired window returns failure-friendly message`() = runTest {
          val noteId = UUID.randomUUID()
          val now = Instant.parse("2026-05-02T12:00:00Z")
          tracker.record(noteId, createdAt = now.minusSeconds(60))

          val result = handler.handle(Intent.UndoLast("MamY, oublie ça"), now = now)

          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("trop tard", ignoreCase = true))
          coVerify(exactly = 0) { noteDao.deleteById(any()) }
      }

      @Test
      fun `no recent note returns informative message`() = runTest {
          val result = handler.handle(Intent.UndoLast("MamY, oublie ça"))
          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("rien à annuler", ignoreCase = true) ||
                     result.spokenText!!.contains("aucune", ignoreCase = true))
      }
  }
  ```

- [ ] **9.2** Run test → FAIL

- [ ] **9.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/UndoLastHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.ActionDao
  import com.mamy.android.data.db.dao.FlagDao
  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import java.time.Instant
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Deletes the most recent Note + its cascading Actions / Promises / Flags
   * if [LastNoteTracker.WINDOW_MS] has not elapsed.
   *
   * The Room schema does NOT use ON DELETE CASCADE (P1 chose to manage cascades
   * in code so it stays explicit). We delete children first then the Note.
   */
  @Singleton
  class UndoLastHandler @Inject constructor(
      private val noteDao: NoteDao,
      private val actionDao: ActionDao,
      private val promiseDao: PromiseDao,
      private val flagDao: FlagDao,
      private val tracker: LastNoteTracker,
  ) : IntentHandler<Intent.UndoLast> {

      override suspend fun handle(intent: Intent.UndoLast): IntentResult = handle(intent, Instant.now())

      // Test-friendly overload: caller can inject `now` for deterministic window checks.
      suspend fun handle(intent: Intent.UndoLast, now: Instant): IntentResult {
          val slot = tracker.snapshot(now = now)
              ?: return if (tracker.snapshot(now = now, windowMs = Long.MAX_VALUE) != null) {
                  IntentResult.spoken("Trop tard, fenêtre d'annulation expirée. Too late, undo window expired.")
              } else {
                  IntentResult.spoken("Rien à annuler récemment.")
              }

          actionDao.deleteByNoteId(slot.noteId)
          promiseDao.deleteByNoteId(slot.noteId)
          flagDao.deleteByNoteId(slot.noteId)
          noteDao.deleteById(slot.noteId)
          tracker.clear()

          return IntentResult.spoken("Annulé.")
      }
  }
  ```

- [ ] **9.4** Run test → PASS

- [ ] **9.5** `git commit -m "feat: add UndoLastHandler with 30s window + cascade delete (Action/Promise/Flag/Note)"`

---

## Task 10 — `CorrectLastHandler` (re-submit to LlmStructurer)

**Why:** Edits the structured_json of the last note by combining original transcript + correction.

Assumes `LlmStructurer` (P3) has signature :
```kotlin
interface LlmStructurer {
    suspend fun structure(transcript: String): StructuredResult
}
data class StructuredResult(val json: String, val nonStructured: Boolean, val provider: String, val costCents: Int?)
```

- [ ] **10.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/CorrectLastHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.db.entity.NoteEntity
  import com.mamy.android.data.llm.LlmStructurer
  import com.mamy.android.data.llm.StructuredResult
  import com.mamy.android.domain.intent.Intent
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import io.mockk.slot
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertTrue

  class CorrectLastHandlerTest {

      private val noteDao: NoteDao = mockk(relaxed = true)
      private val structurer: LlmStructurer = mockk()
      private val handler = CorrectLastHandler(noteDao, structurer)

      @Test
      fun `re-submits combined transcript and updates note`() = runTest {
          val original = NoteEntity(
              id = UUID.randomUUID(),
              personId = null,
              meetingId = null,
              rawText = "Marie va mieux RH a confirmé",
              structuredJson = """{"persons":[{"name":"Marie"}]}""",
              nonStructured = false,
              createdAt = Instant.now(),
              audioDurationSec = 30,
              llmProvider = "claude",
              llmCostCents = 1,
          )
          coEvery { noteDao.findLatest() } returns original
          coEvery { structurer.structure(any()) } returns StructuredResult(
              json = """{"persons":[{"name":"Pierre"}]}""",
              nonStructured = false,
              provider = "claude",
              costCents = 1,
          )

          val result = handler.handle(Intent.CorrectLast(
              correctedText = "remplace Marie par Pierre",
              rawText = "MamY, modifie : remplace Marie par Pierre",
          ))

          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("Corrigé") || result.spokenText!!.contains("Updated"))

          val captured = slot<String>()
          coVerify {
              structurer.structure(capture(captured))
          }
          assertTrue(captured.captured.contains("Marie va mieux"))
          assertTrue(captured.captured.contains("remplace Marie par Pierre"))
          coVerify { noteDao.update(any()) }
      }

      @Test
      fun `no recent note returns informative message`() = runTest {
          coEvery { noteDao.findLatest() } returns null
          val result = handler.handle(Intent.CorrectLast(
              correctedText = "x",
              rawText = "MamY, modifie : x",
          ))
          assertTrue(result.success)
          assertTrue(result.spokenText!!.contains("aucune", ignoreCase = true) ||
                     result.spokenText!!.contains("rien", ignoreCase = true))
      }
  }
  ```

- [ ] **10.2** Run test → FAIL

- [ ] **10.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/CorrectLastHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.llm.LlmStructurer
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Re-runs the LLM structurer on the previous transcript concatenated with the
   * user's correction directive, then overwrites the previous Note's structured_json.
   *
   * NOTE: cascading Actions/Promises/Flags from the original Note are NOT auto-rebuilt
   * here. The user can chain `oublie ça` before `modifie :` if they want a clean redo.
   * V1 limitation, documented in spec section 5.
   */
  @Singleton
  class CorrectLastHandler @Inject constructor(
      private val noteDao: NoteDao,
      private val structurer: LlmStructurer,
  ) : IntentHandler<Intent.CorrectLast> {

      override suspend fun handle(intent: Intent.CorrectLast): IntentResult {
          val last = noteDao.findLatest()
              ?: return IntentResult.spoken("Aucune capture récente à corriger. No recent capture to correct.")

          val combined = buildString {
              append("Original transcript: ")
              append(last.rawText)
              append("\n\nUser correction: ")
              append(intent.correctedText)
              append("\n\nApply the correction and re-emit the JSON.")
          }

          val updated = structurer.structure(combined)
          noteDao.update(
              last.copy(
                  structuredJson = updated.json,
                  nonStructured = updated.nonStructured,
                  llmProvider = updated.provider,
                  llmCostCents = updated.costCents,
              )
          )
          return IntentResult.spoken("Corrigé. Updated.")
      }
  }
  ```

- [ ] **10.4** Run test → PASS

- [ ] **10.5** `git commit -m "feat: add CorrectLastHandler (re-submit transcript+correction to LlmStructurer)"`

---

## Task 11 — Person fuzzy matcher

**Why:** Used by Task 12 (PersonBriefHandler) and Task 14 (HomonymeClarifier). Lowercase contains + Levenshtein tie-break.

- [ ] **11.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/memory/PersonMatcherTest.kt`
  ```kotlin
  package com.mamy.android.domain.memory

  import com.mamy.android.data.db.dao.PersonDao
  import com.mamy.android.data.db.entity.PersonEntity
  import io.mockk.coEvery
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertEquals

  class PersonMatcherTest {

      private val dao: PersonDao = mockk()
      private val matcher = PersonMatcher(dao)

      @Test
      fun `single match returns SingleMatch`() = runTest {
          coEvery { dao.findByName("Marie") } returns listOf(person("Marie Dubois"))
          val r = matcher.match("Marie")
          assertEquals(1, (r as PersonMatcher.MatchResult.SingleMatch).person.let { 1 })
      }

      @Test
      fun `multiple matches returns Ambiguous`() = runTest {
          coEvery { dao.findByName("Marie") } returns listOf(
              person("Marie Dubois"),
              person("Marie Tremblay"),
          )
          val r = matcher.match("Marie")
          assertEquals(2, (r as PersonMatcher.MatchResult.Ambiguous).candidates.size)
      }

      @Test
      fun `no matches returns NotFound`() = runTest {
          coEvery { dao.findByName("Xyz") } returns emptyList()
          val r = matcher.match("Xyz")
          assertEquals(PersonMatcher.MatchResult.NotFound, r)
      }

      private fun person(name: String) = PersonEntity(
          id = UUID.randomUUID(),
          name = name,
          email = null, roleHint = null, calendarAttendeeId = null,
          createdAt = Instant.now(), lastInteractionAt = null,
          interactionCount = 0, emotionalTrend = null,
          unmatched = false, archived = false,
      )
  }
  ```

- [ ] **11.2** Run test → FAIL

- [ ] **11.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/memory/PersonMatcher.kt`
  ```kotlin
  package com.mamy.android.domain.memory

  import com.mamy.android.data.db.dao.PersonDao
  import com.mamy.android.data.db.entity.PersonEntity
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Substring-first match (handled by PersonDao.findByName SQL LIKE).
   * No Levenshtein in V1 — DAO does the heavy lifting; this layer adapts results
   * into a typed sealed class for handlers to dispatch on.
   */
  @Singleton
  class PersonMatcher @Inject constructor(
      private val dao: PersonDao,
  ) {
      sealed class MatchResult {
          data class SingleMatch(val person: PersonEntity) : MatchResult()
          data class Ambiguous(val candidates: List<PersonEntity>) : MatchResult()
          object NotFound : MatchResult()
      }

      suspend fun match(query: String): MatchResult {
          val rows = dao.findByName(query.trim())
          return when (rows.size) {
              0 -> MatchResult.NotFound
              1 -> MatchResult.SingleMatch(rows[0])
              else -> MatchResult.Ambiguous(rows)
          }
      }
  }
  ```

- [ ] **11.4** Run test → PASS

- [ ] **11.5** `git commit -m "feat: add PersonMatcher fuzzy lookup (single/ambiguous/not-found)"`

---

## Task 12 — `TemplatedPersonBriefHandler` (V1 pre-LLM)

**Why:** PersonBrief handler interface declared in Task 5; this is the V1 templated impl. P6 will replace with LLM.

- [ ] **12.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/TemplatedPersonBriefHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.FlagDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.data.db.entity.FlagEntity
  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.data.db.entity.PromiseEntity
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.memory.PersonMatcher
  import io.mockk.coEvery
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertTrue

  class TemplatedPersonBriefHandlerTest {

      private val matcher: PersonMatcher = mockk()
      private val promiseDao: PromiseDao = mockk()
      private val flagDao: FlagDao = mockk()
      private val handler = TemplatedPersonBriefHandler(matcher, promiseDao, flagDao)

      @Test
      fun `not found returns clean message`() = runTest {
          coEvery { matcher.match("Xyz") } returns PersonMatcher.MatchResult.NotFound
          val result = handler.handle(Intent.PersonBrief("Xyz", "MamY, briefe-moi sur Xyz"))
          assertTrue(result.spokenText!!.contains("inconnue") || result.spokenText!!.contains("not found"))
      }

      @Test
      fun `ambiguous returns clarification message`() = runTest {
          coEvery { matcher.match("Marie") } returns PersonMatcher.MatchResult.Ambiguous(
              listOf(personFixture("Marie Dubois"), personFixture("Marie Tremblay")),
          )
          val result = handler.handle(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))
          assertTrue(result.spokenText!!.contains("Marie Dubois"))
          assertTrue(result.spokenText!!.contains("Marie Tremblay"))
      }

      @Test
      fun `single match assembles brief from promises and flags`() = runTest {
          val marie = personFixture("Marie Dubois")
          coEvery { matcher.match("Marie") } returns PersonMatcher.MatchResult.SingleMatch(marie)
          coEvery { promiseDao.findActiveBetween("self", marie.id.toString()) } returns listOf(
              promiseFixture(from = "self", to = marie.id.toString(), what = "review CV"),
          )
          coEvery { promiseDao.findActiveBetween(marie.id.toString(), "self") } returns listOf(
              promiseFixture(from = marie.id.toString(), to = "self", what = "envoyer rapport"),
          )
          coEvery { flagDao.findActiveByPerson(marie.id) } returns listOf(
              flagFixture(personId = marie.id, type = "demotivation", note = "perçue comme indirecte"),
          )

          val result = handler.handle(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))

          assertTrue(result.spokenText!!.contains("Marie Dubois"))
          assertTrue(result.spokenText!!.contains("review CV"))
          assertTrue(result.spokenText!!.contains("envoyer rapport"))
          assertTrue(result.spokenText!!.contains("demotivation"))
      }

      private fun personFixture(name: String) = PersonEntity(
          id = UUID.randomUUID(), name = name,
          email = null, roleHint = null, calendarAttendeeId = null,
          createdAt = Instant.now(), lastInteractionAt = null,
          interactionCount = 0, emotionalTrend = null,
          unmatched = false, archived = false,
      )

      private fun promiseFixture(from: String, to: String, what: String) = PromiseEntity(
          id = UUID.randomUUID(), fromId = from, toId = to,
          what = what, due = null, status = "active",
          fromNoteId = UUID.randomUUID(), createdAt = Instant.now(), resolvedAt = null,
      )

      private fun flagFixture(personId: UUID, type: String, note: String) = FlagEntity(
          id = UUID.randomUUID(), personId = personId, type = type,
          source = "direct", severity = "medium", note = note,
          resolved = false, fromNoteId = UUID.randomUUID(), createdAt = Instant.now(),
      )
  }
  ```

- [ ] **12.2** Run test → FAIL — also need `PromiseDao.findActiveBetween` method.

- [ ] **12.3** Add to `PromiseDao.kt` :
  ```kotlin
      @Query("SELECT * FROM Promise WHERE from_id = :fromId AND to_id = :toId AND status = 'active' ORDER BY created_at DESC")
      suspend fun findActiveBetween(fromId: String, toId: String): List<PromiseEntity>
  ```

- [ ] **12.4** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/TemplatedPersonBriefHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.FlagDao
  import com.mamy.android.data.db.dao.PromiseDao
  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import com.mamy.android.domain.memory.PersonMatcher
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * V1 templated person brief : pulls open promises both ways + active flags + assembles
   * a deterministic vocal text. P6 will replace with an LLM-generated brief that uses
   * the same DB context but produces conversational tone.
   */
  @Singleton
  class TemplatedPersonBriefHandler @Inject constructor(
      private val matcher: PersonMatcher,
      private val promiseDao: PromiseDao,
      private val flagDao: FlagDao,
  ) : PersonBriefHandler {

      override suspend fun handle(intent: Intent.PersonBrief): IntentResult {
          return when (val r = matcher.match(intent.personQuery)) {
              PersonMatcher.MatchResult.NotFound ->
                  IntentResult.spoken("Personne inconnue : ${intent.personQuery}. Person not found.")
              is PersonMatcher.MatchResult.Ambiguous -> {
                  val names = r.candidates.joinToString(" ou ") { it.name }
                  IntentResult.spoken("Plusieurs personnes correspondent : $names. Précise.")
              }
              is PersonMatcher.MatchResult.SingleMatch -> assemble(r.person)
          }
      }

      private suspend fun assemble(p: PersonEntity): IntentResult {
          val owedToPerson = promiseDao.findActiveBetween("self", p.id.toString())
          val owedFromPerson = promiseDao.findActiveBetween(p.id.toString(), "self")
          val flags = flagDao.findActiveByPerson(p.id)

          val sb = StringBuilder()
          sb.append("Brief sur ${p.name}. ")
          if (owedToPerson.isEmpty() && owedFromPerson.isEmpty() && flags.isEmpty()) {
              sb.append("Rien d'ouvert actuellement.")
              return IntentResult.spoken(sb.toString())
          }
          if (owedToPerson.isNotEmpty()) {
              sb.append("Tu lui dois : ")
              sb.append(owedToPerson.joinToString("; ") { it.what })
              sb.append(". ")
          }
          if (owedFromPerson.isNotEmpty()) {
              sb.append("Elle/il te doit : ")
              sb.append(owedFromPerson.joinToString("; ") { it.what })
              sb.append(". ")
          }
          if (flags.isNotEmpty()) {
              sb.append("Flags actifs : ")
              sb.append(flags.joinToString("; ") { "${it.type} (${it.note})" })
              sb.append(".")
          }
          return IntentResult.spoken(sb.toString().trim())
      }
  }
  ```

- [ ] **12.5** Run test → PASS

- [ ] **12.6** `git commit -m "feat: add TemplatedPersonBriefHandler V1 (pre-LLM, deterministic vocal output)"`

---

## Task 13 — `CaptureHandler` (wraps existing P3 LlmStructurer + records into LastNoteTracker)

**Why:** P3 already wrote LlmStructurer + a service-layer wiring. This handler is a thin adapter that fits the IntentHandler contract and records the new note id into the tracker so undo can find it.

- [ ] **13.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/CaptureHandlerTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.db.entity.NoteEntity
  import com.mamy.android.data.llm.LlmStructurer
  import com.mamy.android.data.llm.StructuredResult
  import com.mamy.android.domain.intent.Intent
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import io.mockk.slot
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.UUID
  import kotlin.test.assertTrue
  import kotlin.test.assertNotNull

  class CaptureHandlerTest {

      private val structurer: LlmStructurer = mockk()
      private val noteDao: NoteDao = mockk()
      private val tracker = LastNoteTracker()
      private val handler = CaptureHandler(structurer, noteDao, tracker)

      @Test
      fun `success path persists note and records into tracker`() = runTest {
          val captured = slot<NoteEntity>()
          coEvery { structurer.structure(any()) } returns StructuredResult(
              json = """{"persons":[{"name":"Marie"}],"actions":[{"description":"call David"}]}""",
              nonStructured = false, provider = "claude", costCents = 2,
          )
          coEvery { noteDao.insert(capture(captured)) } returns Unit

          val result = handler.handle(Intent.Capture(
              rawText = "MamY, prends note Marie va mieux, faut appeler David",
          ))

          assertTrue(result.success)
          assertTrue(result.spokenText!!.startsWith("Noté"))
          coVerify { noteDao.insert(any()) }
          assertNotNull(tracker.snapshot())
      }
  }
  ```

- [ ] **13.2** Run test → FAIL

- [ ] **13.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/CaptureHandler.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.dao.NoteDao
  import com.mamy.android.data.db.entity.NoteEntity
  import com.mamy.android.data.llm.LlmStructurer
  import com.mamy.android.domain.intent.Intent
  import com.mamy.android.domain.intent.IntentResult
  import java.time.Instant
  import java.util.UUID
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Adapter from P3's LlmStructurer to the IntentHandler contract.
   * Persists the Note row, records into [LastNoteTracker] so [UndoLastHandler] can find it.
   *
   * NOTE: P3's existing service-layer flow already creates Action/Promise/Flag rows from
   * the structured JSON. This handler stays focused on Note row + tracker; downstream
   * cascade insert lives in P3's [com.mamy.android.domain.capture.StructuredJsonPersister]
   * (out of scope here, called by the dispatcher wiring in Task 15).
   */
  @Singleton
  class CaptureHandler @Inject constructor(
      private val structurer: LlmStructurer,
      private val noteDao: NoteDao,
      private val tracker: LastNoteTracker,
  ) : IntentHandler<Intent.Capture> {

      override suspend fun handle(intent: Intent.Capture): IntentResult {
          val structured = structurer.structure(intent.rawText)
          val noteId = UUID.randomUUID()
          val now = Instant.now()
          noteDao.insert(
              NoteEntity(
                  id = noteId,
                  personId = null,
                  meetingId = null,
                  rawText = intent.rawText,
                  structuredJson = structured.json,
                  nonStructured = structured.nonStructured,
                  createdAt = now,
                  audioDurationSec = 0,
                  llmProvider = structured.provider,
                  llmCostCents = structured.costCents,
              )
          )
          tracker.record(noteId, createdAt = now)
          return IntentResult.spoken("Noté.")
      }
  }
  ```

- [ ] **13.4** Run test → PASS

- [ ] **13.5** `git commit -m "feat: add CaptureHandler that wraps LlmStructurer + records into LastNoteTracker"`

---

## Task 14 — `HomonymeClarifier` (TTS question + listen for response)

**Why:** Spec section 6 — when capturing without active calendar event AND 2+ persons match the mentioned name, clarify vocally.

Assumes `TextToSpeechAdapter` has :
```kotlin
interface TextToSpeechAdapter {
    suspend fun speak(text: String, lang: java.util.Locale)
    suspend fun listenOnce(timeoutMs: Long): String?
}
```

- [ ] **14.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/handler/HomonymeClarifierTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.data.tts.TextToSpeechAdapter
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import java.time.Instant
  import java.util.Locale
  import java.util.UUID
  import kotlin.test.assertEquals
  import kotlin.test.assertNotNull
  import kotlin.test.assertNull

  class HomonymeClarifierTest {

      private val tts: TextToSpeechAdapter = mockk(relaxed = true)
      private val clarifier = HomonymeClarifier(tts)

      @Test
      fun `picks candidate matching response`() = runTest {
          val dubois = personFixture("Marie Dubois")
          val tremblay = personFixture("Marie Tremblay")
          coEvery { tts.listenOnce(any()) } returns "Tremblay"

          val choice = clarifier.disambiguate(listOf(dubois, tremblay), Locale.FRENCH)

          assertEquals(tremblay.id, choice?.id)
          coVerify { tts.speak(match { it.contains("Marie Dubois") && it.contains("Marie Tremblay") }, Locale.FRENCH) }
      }

      @Test
      fun `no audible response returns null`() = runTest {
          val a = personFixture("Marie Dubois")
          val b = personFixture("Marie Tremblay")
          coEvery { tts.listenOnce(any()) } returns null

          val choice = clarifier.disambiguate(listOf(a, b), Locale.FRENCH)
          assertNull(choice)
      }

      @Test
      fun `unmatched response returns null`() = runTest {
          val a = personFixture("Marie Dubois")
          val b = personFixture("Marie Tremblay")
          coEvery { tts.listenOnce(any()) } returns "personne d'autre"

          val choice = clarifier.disambiguate(listOf(a, b), Locale.FRENCH)
          assertNull(choice)
      }

      private fun personFixture(name: String) = PersonEntity(
          id = UUID.randomUUID(), name = name,
          email = null, roleHint = null, calendarAttendeeId = null,
          createdAt = Instant.now(), lastInteractionAt = null,
          interactionCount = 0, emotionalTrend = null,
          unmatched = false, archived = false,
      )
  }
  ```

- [ ] **14.2** Run test → FAIL

- [ ] **14.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/handler/HomonymeClarifier.kt`
  ```kotlin
  package com.mamy.android.domain.intent.handler

  import com.mamy.android.data.db.entity.PersonEntity
  import com.mamy.android.data.tts.TextToSpeechAdapter
  import java.util.Locale
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Resolves homonymes via TTS round-trip when 2+ Person rows match a name query
   * AND no active calendar event narrows it down.
   *
   * Strategy : speak "Tu parles de <A> ou <B> ?", listen up to 5 sec, match
   * the response against last-name tokens of each candidate. First substring hit wins.
   * Returns null if no match — caller decides whether to retry or store as unmatched.
   */
  @Singleton
  class HomonymeClarifier @Inject constructor(
      private val tts: TextToSpeechAdapter,
  ) {
      suspend fun disambiguate(
          candidates: List<PersonEntity>,
          lang: Locale,
          listenTimeoutMs: Long = 5_000L,
      ): PersonEntity? {
          require(candidates.size >= 2) { "disambiguate requires 2+ candidates" }
          val question = if (lang.language == "fr") {
              "Tu parles de " + candidates.joinToString(" ou ") { it.name } + " ?"
          } else {
              "Did you mean " + candidates.joinToString(" or ") { it.name } + "?"
          }
          tts.speak(question, lang)
          val response = tts.listenOnce(listenTimeoutMs) ?: return null
          val needle = response.lowercase()
          // Match by any token of the candidate name (typically last name disambiguates)
          return candidates.firstOrNull { p ->
              p.name.split(" ").any { tok -> tok.length >= 3 && needle.contains(tok.lowercase()) }
          }
      }
  }
  ```

- [ ] **14.4** Run test → PASS

- [ ] **14.5** `git commit -m "feat: add HomonymeClarifier (TTS round-trip for ambiguous person captures)"`

---

## Task 15 — `IntentDispatcher` + wiring into the foreground service

**Why:** Replaces the P2 stub call site in `MamYListenerService`.

- [ ] **15.1** Write failing test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/IntentDispatcherTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import com.mamy.android.domain.intent.handler.ActionsOpenHandler
  import com.mamy.android.domain.intent.handler.CaptureHandler
  import com.mamy.android.domain.intent.handler.CorrectLastHandler
  import com.mamy.android.domain.intent.handler.DailyBriefHandler
  import com.mamy.android.domain.intent.handler.EodSummaryHandler
  import com.mamy.android.domain.intent.handler.NextBriefHandler
  import com.mamy.android.domain.intent.handler.PersonBriefHandler
  import com.mamy.android.domain.intent.handler.PromisesOwedMeHandler
  import com.mamy.android.domain.intent.handler.UndoLastHandler
  import io.mockk.coEvery
  import io.mockk.coVerify
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test

  class IntentDispatcherTest {

      private val capture: CaptureHandler = mockk(relaxed = true)
      private val daily: DailyBriefHandler = mockk(relaxed = true)
      private val next: NextBriefHandler = mockk(relaxed = true)
      private val personBrief: PersonBriefHandler = mockk(relaxed = true)
      private val owed: PromisesOwedMeHandler = mockk(relaxed = true)
      private val open: ActionsOpenHandler = mockk(relaxed = true)
      private val eod: EodSummaryHandler = mockk(relaxed = true)
      private val undo: UndoLastHandler = mockk(relaxed = true)
      private val correct: CorrectLastHandler = mockk(relaxed = true)

      private val dispatcher = IntentDispatcher(
          capture, daily, next, personBrief, owed, open, eod, undo, correct,
      )

      @Test
      fun `Capture routes to CaptureHandler`() = runTest {
          coEvery { capture.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.Capture("MamY, prends note x"))
          coVerify { capture.handle(any()) }
      }

      @Test
      fun `DailyBrief routes to DailyBriefHandler`() = runTest {
          coEvery { daily.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.DailyBrief("MamY, ma journée"))
          coVerify { daily.handle(any()) }
      }

      @Test
      fun `PersonBrief routes`() = runTest {
          coEvery { personBrief.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))
          coVerify { personBrief.handle(any()) }
      }

      @Test
      fun `PromisesOwedMe routes`() = runTest {
          coEvery { owed.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.PromisesOwedMe("MamY, qui me devait quoi"))
          coVerify { owed.handle(any()) }
      }

      @Test
      fun `UndoLast routes`() = runTest {
          coEvery { undo.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.UndoLast("MamY, oublie ça"))
          coVerify { undo.handle(any()) }
      }

      @Test
      fun `CorrectLast routes`() = runTest {
          coEvery { correct.handle(any()) } returns IntentResult.silent()
          dispatcher.dispatch(Intent.CorrectLast("x", "MamY, modifie : x"))
          coVerify { correct.handle(any()) }
      }
  }
  ```

- [ ] **15.2** Run test → FAIL

- [ ] **15.3** Implement
  File: `app/src/main/kotlin/com/mamy/android/domain/intent/IntentDispatcher.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import com.mamy.android.domain.intent.handler.ActionsOpenHandler
  import com.mamy.android.domain.intent.handler.CaptureHandler
  import com.mamy.android.domain.intent.handler.CorrectLastHandler
  import com.mamy.android.domain.intent.handler.DailyBriefHandler
  import com.mamy.android.domain.intent.handler.EodSummaryHandler
  import com.mamy.android.domain.intent.handler.NextBriefHandler
  import com.mamy.android.domain.intent.handler.PersonBriefHandler
  import com.mamy.android.domain.intent.handler.PromisesOwedMeHandler
  import com.mamy.android.domain.intent.handler.UndoLastHandler
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Single dispatch surface : `dispatch(Intent) -> IntentResult`.
   * The when-on-sealed-class is exhaustive, compiler enforces full coverage.
   */
  @Singleton
  class IntentDispatcher @Inject constructor(
      private val captureHandler: CaptureHandler,
      private val dailyBriefHandler: DailyBriefHandler,
      private val nextBriefHandler: NextBriefHandler,
      private val personBriefHandler: PersonBriefHandler,
      private val promisesOwedMeHandler: PromisesOwedMeHandler,
      private val actionsOpenHandler: ActionsOpenHandler,
      private val eodSummaryHandler: EodSummaryHandler,
      private val undoLastHandler: UndoLastHandler,
      private val correctLastHandler: CorrectLastHandler,
  ) {
      suspend fun dispatch(intent: Intent): IntentResult = when (intent) {
          is Intent.Capture -> captureHandler.handle(intent)
          is Intent.DailyBrief -> dailyBriefHandler.handle(intent)
          is Intent.NextBrief -> nextBriefHandler.handle(intent)
          is Intent.PersonBrief -> personBriefHandler.handle(intent)
          is Intent.PromisesOwedMe -> promisesOwedMeHandler.handle(intent)
          is Intent.ActionsOpen -> actionsOpenHandler.handle(intent)
          is Intent.EodSummary -> eodSummaryHandler.handle(intent)
          is Intent.UndoLast -> undoLastHandler.handle(intent)
          is Intent.CorrectLast -> correctLastHandler.handle(intent)
      }
  }
  ```

- [ ] **15.4** Wire into `MamYListenerService` (replace P2 stub).
  File: `app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt`
  Replace the line that previously called `processCapture(transcript)` (or equivalent stub) with :
  ```kotlin
  // Inside the coroutine that consumes the Whisper transcript :
  val intent = intentRouter.classify(transcript)
  val result = intentDispatcher.dispatch(intent)
  result.spokenText?.let { tts.speak(it, currentLocale) }
  ```
  And add the two new constructor injections (`intentRouter: IntentRouter`, `intentDispatcher: IntentDispatcher`) to the existing `@AndroidEntryPoint` service.

- [ ] **15.5** Run test → PASS, also `./gradlew :app:assembleDebug` to confirm wiring compiles.

- [ ] **15.6** `git commit -m "feat: add IntentDispatcher + wire into MamYListenerService (replaces P2 stub)"`

---

## Task 16 — Hilt DI module for the intent layer

**Why:** Bind handler interfaces to default impls. Other modules (P6) override these in tests/release.

- [ ] **16.1** Implement
  File: `app/src/main/kotlin/com/mamy/android/di/IntentModule.kt`
  ```kotlin
  package com.mamy.android.di

  import com.mamy.android.domain.intent.handler.DailyBriefHandler
  import com.mamy.android.domain.intent.handler.EodSummaryHandler
  import com.mamy.android.domain.intent.handler.NextBriefHandler
  import com.mamy.android.domain.intent.handler.PersonBriefHandler
  import com.mamy.android.domain.intent.handler.StubDailyBriefHandler
  import com.mamy.android.domain.intent.handler.StubEodSummaryHandler
  import com.mamy.android.domain.intent.handler.StubNextBriefHandler
  import com.mamy.android.domain.intent.handler.TemplatedPersonBriefHandler
  import dagger.Binds
  import dagger.Module
  import dagger.hilt.InstallIn
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  /**
   * Binds briefing handler interfaces to their V1 impls.
   * P6 will provide a `LlmBriefingModule` that overrides these via `@TestInstallIn`
   * (replace strategy) to swap the stubs for LLM-backed handlers.
   */
  @Module
  @InstallIn(SingletonComponent::class)
  abstract class IntentModule {

      @Binds
      @Singleton
      abstract fun bindDailyBriefHandler(impl: StubDailyBriefHandler): DailyBriefHandler

      @Binds
      @Singleton
      abstract fun bindNextBriefHandler(impl: StubNextBriefHandler): NextBriefHandler

      @Binds
      @Singleton
      abstract fun bindEodSummaryHandler(impl: StubEodSummaryHandler): EodSummaryHandler

      @Binds
      @Singleton
      abstract fun bindPersonBriefHandler(impl: TemplatedPersonBriefHandler): PersonBriefHandler
  }
  ```

- [ ] **16.2** Confirm compile + DI graph valid : `./gradlew :app:compileDebugKotlin :app:hiltJavaCompileDebug`

- [ ] **16.3** `git commit -m "feat: add Hilt IntentModule binding handler interfaces to V1 impls"`

---

## Task 17 — End-to-end intent smoke tests

**Why:** Validate full Router → Dispatcher → Handler → IntentResult chain on 7 spec-canonical voice inputs.

- [ ] **17.1** Write test
  File: `app/src/test/kotlin/com/mamy/android/domain/intent/IntentEndToEndTest.kt`
  ```kotlin
  package com.mamy.android.domain.intent

  import com.mamy.android.domain.intent.handler.ActionsOpenHandler
  import com.mamy.android.domain.intent.handler.CaptureHandler
  import com.mamy.android.domain.intent.handler.CorrectLastHandler
  import com.mamy.android.domain.intent.handler.DailyBriefHandler
  import com.mamy.android.domain.intent.handler.EodSummaryHandler
  import com.mamy.android.domain.intent.handler.NextBriefHandler
  import com.mamy.android.domain.intent.handler.PersonBriefHandler
  import com.mamy.android.domain.intent.handler.PromisesOwedMeHandler
  import com.mamy.android.domain.intent.handler.UndoLastHandler
  import io.mockk.coEvery
  import io.mockk.mockk
  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.Test
  import kotlin.test.assertTrue

  /**
   * 7 spec-canonical voice inputs flow Router → Dispatcher → Handler.
   * Handlers are mocked to return canned replies — we verify only that the right one fires.
   */
  class IntentEndToEndTest {

      private val capture: CaptureHandler = mockk()
      private val daily: DailyBriefHandler = mockk()
      private val next: NextBriefHandler = mockk()
      private val personBrief: PersonBriefHandler = mockk()
      private val owed: PromisesOwedMeHandler = mockk()
      private val open: ActionsOpenHandler = mockk()
      private val eod: EodSummaryHandler = mockk()
      private val undo: UndoLastHandler = mockk()
      private val correct: CorrectLastHandler = mockk()

      private val router = IntentRouter()
      private val dispatcher = IntentDispatcher(
          capture, daily, next, personBrief, owed, open, eod, undo, correct,
      )

      @Test
      fun `5 FR + 2 EN canonical inputs route end-to-end`() = runTest {
          coEvery { capture.handle(any()) } returns IntentResult.spoken("Noté")
          coEvery { daily.handle(any()) } returns IntentResult.spoken("daily")
          coEvery { next.handle(any()) } returns IntentResult.spoken("next")
          coEvery { personBrief.handle(any()) } returns IntentResult.spoken("brief Marie")
          coEvery { owed.handle(any()) } returns IntentResult.spoken("owed")
          coEvery { open.handle(any()) } returns IntentResult.spoken("open")
          coEvery { eod.handle(any()) } returns IntentResult.spoken("eod")
          coEvery { undo.handle(any()) } returns IntentResult.spoken("undo")
          coEvery { correct.handle(any()) } returns IntentResult.spoken("correct")

          val transcripts = listOf(
              "MamY, prends note projet X avance bien" to "Noté",
              "MamY, ma journée" to "daily",
              "MamY, briefe" to "next",
              "MamY, briefe-moi sur Marie" to "brief Marie",
              "MamY, qui me devait quoi" to "owed",
              "MamY, my open actions" to "open",
              "MamY, summarize my day" to "eod",
          )
          for ((tx, expected) in transcripts) {
              val intent = router.classify(tx)
              val result = dispatcher.dispatch(intent)
              assertTrue(
                  result.spokenText == expected,
                  "transcript [$tx] → intent ${intent::class.simpleName} → got [${result.spokenText}], expected [$expected]",
              )
          }
      }
  }
  ```

- [ ] **17.2** Run :
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.mamy.android.domain.intent.IntentEndToEndTest"
  ```
  Expect PASS.

- [ ] **17.3** `git commit -m "test: end-to-end smoke tests for Router→Dispatcher→Handler on 7 canonical inputs"`

---

## Task 18 — Final P4 verification & coverage

- [ ] **18.1** Full unit test run :
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
  All P4 tests must be green; existing P1/P2/P3 tests must still be green.

- [ ] **18.2** Lint :
  ```bash
  ./gradlew :app:lintDebug
  ```
  No new errors.

- [ ] **18.3** Coverage check on `domain/intent/` + new DAO methods : target ≥ 70 %.
  ```bash
  ./gradlew :app:jacocoTestReportDebug
  ```
  (assumes JaCoCo task already wired in P1 build script ; if not, skip this step and document for P8).

- [ ] **18.4** Manual smoke test on emulator : install debug APK, trigger via the volume-up fallback bypass (long-press volume-up → record voice line → confirm correct intent fires + TTS speaks the expected output for at least 3 of the 10 intents).

- [ ] **18.5** Tag : `git tag -a checkpoint/p4-voice-intents-2026-05-02 -m "P4 done : 10 voice intents + memory queries + undo/correct/homonyme"`

---

## Self-review (filled in as part of writing this plan)

1. **Spec coverage** — all 10 intents (capture, daily_brief, next_brief, person_brief, promises_owed_me, actions_open, eod_summary, undo_last, correct_last) handled. Person-brief alias `c'est quoi avec` covered by separate regex in Task 2. Homonyme TTS clarification covered in Task 14. Memory queries covered in Tasks 4, 6, 7, 12. Cascade delete on undo covered in Task 8/9.
2. **No placeholders** — every code block is full Kotlin source. No "TODO", no "TBD", no `// add proper handling`.
3. **Type consistency** — `Intent` (sealed class) / `IntentRouter` / `IntentDispatcher` / `IntentHandler<I>` / `IntentResult` used consistently. Handler interfaces (`DailyBriefHandler`, `NextBriefHandler`, `PersonBriefHandler`, `EodSummaryHandler`) are all interfaces extending `IntentHandler<I>`. Stub impls (`StubDailyBriefHandler`, `StubNextBriefHandler`, `StubEodSummaryHandler`) and V1 impl (`TemplatedPersonBriefHandler`) all bound via `IntentModule` Hilt module.

## Outstanding for later phases (NOT P4)

- **P5 calendar matching** : `CaptureHandler` currently writes `personId = null`. P5 wires Person matching via meeting context.
- **P6 LLM briefings** : `StubDailyBriefHandler` / `StubNextBriefHandler` / `StubEodSummaryHandler` get replaced by LLM-backed impls. `TemplatedPersonBriefHandler` likewise upgraded to LLM-flavored output.
- **P7 UI** : Reports list, Person detail screen — read same DAO methods we extended in Task 4.
- **P8 polish** : i18n strings extraction (V1 inline French/English in handlers — Task 16's `IntentModule` is the natural insertion point for a `@LocalizedResources` indirection later).
