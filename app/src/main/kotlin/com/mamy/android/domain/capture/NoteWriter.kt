package com.mamy.android.domain.capture

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.llm.cost.LlmCostCalculator
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a [StructureOutcome] to the local DB. Returns the inserted Note id, or null on Failure.
 *
 * For Success : creates/updates persons by name, inserts Note + child rows.
 * For RawFallback : inserts Note with `nonStructured=true`, no children.
 * For Failure : writes nothing.
 */
@Singleton
class NoteWriter @Inject constructor(
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val calculator: LlmCostCalculator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun write(outcome: StructureOutcome, transcript: String, durationSec: Int): UUID? {
        return when (outcome) {
            is StructureOutcome.Success -> writeSuccess(outcome, transcript, durationSec)
            is StructureOutcome.RawFallback -> writeRaw(outcome, transcript, durationSec)
            is StructureOutcome.Failure -> null
        }
    }

    private suspend fun writeSuccess(o: StructureOutcome.Success, transcript: String, durationSec: Int): UUID {
        val now = clock.instant()
        val note = o.note

        // 1. Resolve / create persons by name
        val personIds = mutableMapOf<String, UUID>()
        for (p in note.persons) {
            personIds[p.name] = upsertPerson(p.name, p.roleHint, now)
        }
        // Also resolve named assignees / linkedPerson / promise from-to / flag person
        val extraNames = buildList {
            note.actions.forEach {
                if (it.assignee != "self") add(it.assignee)
                it.linkedPerson?.let(::add)
            }
            note.promises.forEach {
                if (it.from != "self") add(it.from)
                if (it.to != "self") add(it.to)
            }
            note.flags.forEach { add(it.person) }
            note.meetingMeta.personMain?.let(::add)
        }.distinct()
        for (name in extraNames) {
            if (!personIds.containsKey(name)) personIds[name] = upsertPerson(name, null, now)
        }

        val mainPersonId = note.meetingMeta.personMain?.let { personIds[it] }

        // 2. Insert Note
        val noteId = UUID.randomUUID()
        val costMicro = calculator.microCents(o.providerId, o.tokensIn, o.tokensOut)
        noteDao.insert(NoteEntity(
            id = noteId,
            personId = mainPersonId,
            meetingId = null,
            rawText = transcript,
            structuredJson = o.rawText,
            nonStructured = false,
            createdAt = now,
            audioDurationSec = durationSec,
            llmProvider = o.providerId,
            llmCostCents = (costMicro / 10_000L).toInt(),
        ))

        // 3. Insert Actions
        for (a in note.actions) {
            actionDao.insert(ActionEntity(
                id = UUID.randomUUID(),
                description = a.description,
                assignee = a.assignee,
                linkedPersonId = a.linkedPerson?.let(personIds::get),
                deadline = parseInstant(a.deadline),
                status = "open",
                fromNoteId = noteId,
                createdAt = now,
                doneAt = null,
            ))
        }

        // 4. Insert Promises
        for (p in note.promises) {
            promiseDao.insert(PromiseEntity(
                id = UUID.randomUUID(),
                fromId = if (p.from == "self") "self" else (personIds[p.from]?.toString() ?: p.from),
                toId = if (p.to == "self") "self" else (personIds[p.to]?.toString() ?: p.to),
                what = p.what,
                due = parseInstant(p.due),
                status = "active",
                fromNoteId = noteId,
                createdAt = now,
                resolvedAt = null,
            ))
        }

        // 5. Insert Flags
        for (f in note.flags) {
            val pid = personIds[f.person] ?: upsertPerson(f.person, null, now).also { personIds[f.person] = it }
            flagDao.insert(FlagEntity(
                id = UUID.randomUUID(),
                personId = pid,
                type = f.type.name.lowercase(),
                source = f.source,
                severity = f.severity.name.lowercase(),
                note = f.note,
                resolved = false,
                fromNoteId = noteId,
                createdAt = now,
            ))
        }

        return noteId
    }

    private suspend fun writeRaw(o: StructureOutcome.RawFallback, transcript: String, durationSec: Int): UUID {
        val noteId = UUID.randomUUID()
        noteDao.insert(NoteEntity(
            id = noteId,
            personId = null,
            meetingId = null,
            rawText = transcript,
            structuredJson = o.rawText,
            nonStructured = true,
            createdAt = clock.instant(),
            audioDurationSec = durationSec,
            llmProvider = o.providerId,
            llmCostCents = null,
        ))
        return noteId
    }

    private suspend fun upsertPerson(name: String, roleHint: String?, now: Instant): UUID {
        val existing = personDao.findByName(name)
        if (existing != null) return existing.id
        val id = UUID.randomUUID()
        personDao.insert(PersonEntity(
            id = id,
            name = name,
            email = null,
            roleHint = roleHint,
            calendarAttendeeId = null,
            createdAt = now,
            lastInteractionAt = now,
            interactionCount = 1,
            emotionalTrend = null,
            unmatched = true,
            archived = false,
        ))
        return id
    }

    private fun parseInstant(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
