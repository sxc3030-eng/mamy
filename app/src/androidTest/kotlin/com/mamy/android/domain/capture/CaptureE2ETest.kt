package com.mamy.android.domain.capture

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.EmotionalState
import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.MeetingMeta
import com.mamy.android.data.llm.model.Severity
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.llm.model.StructuredPerson
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureE2ETest {

    private lateinit var db: MamYDatabase

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, MamYDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun teardown() { db.close() }

    @Test
    fun fullFlow_capturedTranscript_yieldsRowsInDb() = runTest {
        val sample = StructuredNote(
            persons = listOf(
                StructuredPerson(name = "Marie", roleHint = "team lead", emotionalState = EmotionalState.STRESSED, contextAdded = "projet X")
            ),
            actions = listOf(
                StructuredAction(description = "parler à David", assignee = "self", linkedPerson = "Marie")
            ),
            flags = listOf(
                StructuredFlag(person = "Pierre", type = FlagType.DEMOTIVATION, source = "indirect:Marie", severity = Severity.MEDIUM, note = "traîne sur mockup")
            ),
            meetingMeta = MeetingMeta(personMain = "Marie"),
        )

        val provider = mockk<LlmProvider>()
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.success(
            LlmResponse(note = sample, rawText = "raw", tokensIn = 200, tokensOut = 100)
        )
        val factory = mockk<LlmProviderFactory> { every { byId("claude") } returns provider }
        val settings = mockk<SettingsRepository> {
            every { stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "fr"))
        }
        val tracker = LlmCostTracker(db.llmCostDao(), LlmCostCalculator())
        val structurer = LlmStructurer(factory, settings, tracker, PromptBuilder())
        val writer = NoteWriter(
            personDao = db.personDao(),
            noteDao = db.noteDao(),
            actionDao = db.actionDao(),
            promiseDao = db.promiseDao(),
            flagDao = db.flagDao(),
            calculator = LlmCostCalculator(),
        )

        val outcome = structurer.structure("Marie va mieux, parler à David, Pierre traîne", Lang.FR)
        assertTrue(outcome is StructureOutcome.Success)
        val noteId = writer.write(outcome, transcript = "raw text", durationSec = 50)
        assertTrue(noteId != null)

        val people = db.personDao().getAll()
        assertEquals(2, people.size)                 // Marie + Pierre
        val notes = db.noteDao().getAll()
        assertEquals(1, notes.size)
        val actions = db.actionDao().getAll()
        assertEquals(1, actions.size)
        val flags = db.flagDao().getAll()
        assertEquals(1, flags.size)
    }
}
