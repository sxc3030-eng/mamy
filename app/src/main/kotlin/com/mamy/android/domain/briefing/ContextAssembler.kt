package com.mamy.android.domain.briefing

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the JSON context payload sent to the LLM in [BriefingPromptBuilder].
 * Produces a stable, alphabetically-key-ordered JSON string. Uses org.json
 * (no Jackson/Moshi) to keep the dependency surface tight; the LLM does not
 * care about the JSON shape as long as it's parseable.
 */
@Singleton
class ContextAssembler @Inject constructor(
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val meetingDao: MeetingDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun assemble(request: BriefingRequest): String = when (request.type) {
        BriefingType.DAILY        -> assembleDaily(request.now)
        BriefingType.PRE_MEETING  -> assemblePreMeeting(requireNotNull(request.targetId) { "PRE_MEETING needs Meeting.id" })
        BriefingType.PERSON_QUERY -> assemblePersonQuery(requireNotNull(request.targetId) { "PERSON_QUERY needs Person.id" })
        BriefingType.EOD_SUMMARY  -> assembleEod(request.now)
    }

    // -------- DAILY --------

    private suspend fun assembleDaily(now: Instant): String {
        val today = now.atZone(zoneId).toLocalDate()
        val (start, end) = today.bounds()
        val meetings = meetingDao.between(start, end)
        val arr = JSONArray()
        for (m in meetings) {
            val attendees = meetingDao.attendeesOf(m.id)
            for (personId in attendees) {
                val person = personDao.byId(personId) ?: continue
                val notes = noteDao.lastNForPerson(personId, limit = 3)
                val openPromisesFromMe = promiseDao.openFromTo(fromId = "self", toId = personId.toString())
                val openPromisesToMe   = promiseDao.openFromTo(fromId = personId.toString(), toId = "self")
                val openFlags = flagDao.openForPerson(personId)
                arr.put(JSONObject().apply {
                    put("meeting_starts_at", m.startsAt.toString())
                    put("person_name", person.name)
                    put("person_role", person.roleHint ?: JSONObject.NULL)
                    put("recent_notes", JSONArray().also { a -> notes.forEach { a.put(it.rawText) } })
                    put("open_promises_from_me", JSONArray().also { a -> openPromisesFromMe.forEach { a.put(it.what) } })
                    put("open_promises_to_me",   JSONArray().also { a -> openPromisesToMe.forEach { a.put(it.what) } })
                    put("open_flags", JSONArray().also { a -> openFlags.forEach { f ->
                        a.put(JSONObject().apply {
                            put("type", f.type); put("severity", f.severity); put("note", f.note)
                        })
                    } })
                })
            }
        }
        return JSONObject().put("date", today.toString()).put("meetings", arr).toString()
    }

    // -------- PRE_MEETING --------

    private suspend fun assemblePreMeeting(meetingId: String): String {
        val m = meetingDao.byId(java.util.UUID.fromString(meetingId))
            ?: return JSONObject().put("error", "meeting_not_found").toString()
        val attendees = meetingDao.attendeesOf(m.id).map { personDao.byId(it) }.filterNotNull()
        // V1: 1:1s only — pick the first attendee that isn't "self"
        val person = attendees.firstOrNull() ?: return JSONObject().put("error", "no_attendee").toString()
        val notes = noteDao.lastNForPerson(person.id, limit = 5)
        val openPromisesFromMe = promiseDao.openFromTo("self", person.id.toString())
        val openPromisesToMe   = promiseDao.openFromTo(person.id.toString(), "self")
        val openFlags = flagDao.openForPerson(person.id)
        return JSONObject().apply {
            put("meeting_starts_at", m.startsAt.toString())
            put("person_name", person.name)
            put("person_role", person.roleHint ?: JSONObject.NULL)
            put("emotional_trend", person.emotionalTrend ?: JSONObject.NULL)
            put("last_interaction_at", person.lastInteractionAt?.toString() ?: JSONObject.NULL)
            put("recent_notes", JSONArray().also { a -> notes.forEach { a.put(it.rawText) } })
            put("open_promises_from_me", JSONArray().also { a -> openPromisesFromMe.forEach { a.put(it.what) } })
            put("open_promises_to_me",   JSONArray().also { a -> openPromisesToMe.forEach { a.put(it.what) } })
            put("open_flags", JSONArray().also { a -> openFlags.forEach { f ->
                a.put(JSONObject().apply {
                    put("type", f.type); put("severity", f.severity); put("note", f.note)
                })
            } })
        }.toString()
    }

    // -------- PERSON_QUERY --------

    private suspend fun assemblePersonQuery(personId: String): String {
        val pid = java.util.UUID.fromString(personId)
        val p = personDao.byId(pid) ?: return JSONObject().put("error", "person_not_found").toString()
        val notes = noteDao.lastNForPerson(pid, limit = 10)
        val allPromisesFromMe = promiseDao.allFromTo("self", pid.toString())
        val allPromisesToMe   = promiseDao.allFromTo(pid.toString(), "self")
        val flags = flagDao.allForPerson(pid)
        val actions = actionDao.linkedTo(pid)
        return JSONObject().apply {
            put("person_name", p.name)
            put("role", p.roleHint ?: JSONObject.NULL)
            put("interaction_count", p.interactionCount)
            put("last_interaction_at", p.lastInteractionAt?.toString() ?: JSONObject.NULL)
            put("emotional_trend", p.emotionalTrend ?: JSONObject.NULL)
            put("recent_notes", JSONArray().also { a -> notes.forEach { a.put(it.rawText) } })
            put("promises_from_me", JSONArray().also { a -> allPromisesFromMe.forEach { pr ->
                a.put(JSONObject().apply { put("what", pr.what); put("status", pr.status) })
            } })
            put("promises_to_me", JSONArray().also { a -> allPromisesToMe.forEach { pr ->
                a.put(JSONObject().apply { put("what", pr.what); put("status", pr.status) })
            } })
            put("flags", JSONArray().also { a -> flags.forEach { f ->
                a.put(JSONObject().apply {
                    put("type", f.type); put("severity", f.severity)
                    put("note", f.note); put("resolved", f.resolved)
                })
            } })
            put("actions", JSONArray().also { a -> actions.forEach { ac ->
                a.put(JSONObject().apply {
                    put("description", ac.description); put("status", ac.status)
                    put("deadline", ac.deadline?.toString() ?: JSONObject.NULL)
                })
            } })
        }.toString()
    }

    // -------- EOD_SUMMARY --------

    private suspend fun assembleEod(now: Instant): String {
        val today = now.atZone(zoneId).toLocalDate()
        val (start, end) = today.bounds()
        val notes = noteDao.between(start, end)
        val actions = actionDao.createdBetween(start, end)
        val openActions = actions.count { it.status == "open" }
        val doneActions = actions.count { it.status == "done" }
        val promisesUpdated = promiseDao.updatedBetween(start, end)
        return JSONObject().apply {
            put("date", today.toString())
            put("notes_count", notes.size)
            put("actions_open", openActions)
            put("actions_done", doneActions)
            put("promises_updated", JSONArray().also { a -> promisesUpdated.forEach { pr ->
                a.put(JSONObject().apply {
                    put("what", pr.what); put("status", pr.status)
                    put("from", pr.fromId); put("to", pr.toId)
                })
            } })
            put("recent_note_excerpts", JSONArray().also { a -> notes.take(5).forEach { a.put(it.rawText) } })
        }.toString()
    }

    private fun LocalDate.bounds(): Pair<Instant, Instant> {
        val start = this.atStartOfDay(zoneId).toInstant()
        val end = this.plusDays(1).atStartOfDay(zoneId).toInstant()
        return start to end
    }
}
