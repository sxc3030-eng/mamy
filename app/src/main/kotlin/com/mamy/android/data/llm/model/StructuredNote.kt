package com.mamy.android.data.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EmotionalState {
    @SerialName("ok") OK,
    @SerialName("stressed") STRESSED,
    @SerialName("demotivated") DEMOTIVATED,
    @SerialName("happy") HAPPY,
    @SerialName("conflict") CONFLICT,
    @SerialName("engaged") ENGAGED,
    @SerialName("disengaged") DISENGAGED,
}

@Serializable
enum class FlagType {
    @SerialName("demotivation") DEMOTIVATION,
    @SerialName("conflict") CONFLICT,
    @SerialName("risk") RISK,
    @SerialName("opportunity") OPPORTUNITY,
    @SerialName("burnout") BURNOUT,
    @SerialName("growth") GROWTH,
}

@Serializable
enum class Severity {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

@Serializable
data class StructuredPerson(
    val name: String,
    @SerialName("role_hint") val roleHint: String? = null,
    @SerialName("emotional_state") val emotionalState: EmotionalState = EmotionalState.OK,
    @SerialName("context_added") val contextAdded: String = "",
)

@Serializable
data class StructuredAction(
    val description: String,
    val assignee: String,
    val deadline: String? = null,            // ISO8601 string, parsed by caller
    @SerialName("linked_person") val linkedPerson: String? = null,
)

@Serializable
data class StructuredPromise(
    val from: String,
    val to: String,
    val what: String,
    val due: String? = null,                 // ISO8601 string
)

@Serializable
data class StructuredFlag(
    val person: String,
    val type: FlagType,
    val source: String,                      // "direct" or "indirect:<name>"
    val severity: Severity = Severity.MEDIUM,
    val note: String = "",
)

@Serializable
data class MeetingMeta(
    @SerialName("person_main") val personMain: String? = null,
    @SerialName("date_inferred") val dateInferred: String? = null,
)

@Serializable
data class StructuredNote(
    val persons: List<StructuredPerson> = emptyList(),
    val actions: List<StructuredAction> = emptyList(),
    val promises: List<StructuredPromise> = emptyList(),
    val flags: List<StructuredFlag> = emptyList(),
    @SerialName("meeting_meta") val meetingMeta: MeetingMeta = MeetingMeta(),
)
