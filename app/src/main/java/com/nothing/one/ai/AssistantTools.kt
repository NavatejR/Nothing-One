package com.nothing.one.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local actions the assistant may propose at the end of a reply. The model
 * is instructed to append a fenced JSON block when the user's ask maps to a
 * tool; parsing is strict and anything unparseable is ignored — a broken
 * tool call must never corrupt a normal chat answer.
 */
@Serializable
sealed interface AssistantTool {

    @Serializable
    @SerialName("create_note")
    data class CreateNote(val title: String, val body: String) : AssistantTool

    @Serializable
    @SerialName("add_event")
    data class AddEvent(
        val title: String,
        /** ISO-like local date "yyyy-MM-dd" (parsed leniently). */
        val date: String,
        /** 24h "HH:mm", defaults to 09:00. */
        val time: String = "09:00",
        val durationMinutes: Long = 60,
    ) : AssistantTool

    @Serializable
    @SerialName("start_focus")
    data class StartFocus(val minutes: Int = 25) : AssistantTool
}

@Singleton
class AssistantTools @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "tool"
    }

    /**
     * Extracts the last fenced ```json block from [raw] and parses it into an
     * [AssistantTool]. Returns null when the reply carries no (valid) tool.
     */
    fun parse(raw: String): AssistantTool? {
        val fenced = Regex("```json\\s*\\r?\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(raw)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: return null
        return runCatching { json.decodeFromString<AssistantTool>(fenced) }.getOrNull()
    }

    /** The reply with any tool block stripped — what we show as chat text. */
    fun stripToolBlock(raw: String): String =
        raw.replace(Regex("```json\\s*\\r?\\n?.*?```", RegexOption.DOT_MATCHES_ALL), "").trim()

    /**
     * The instruction appended to the system prompt teaching the model when
     * and how to emit tool calls.
     */
    fun toolInstruction(): String = """
        |When the user asks you to create something (a note, an event) or to
        |start a focus session, end your reply with a fenced json block:
        |```json
        |{"tool":"create_note","title":"...","body":"..."}
        |```
        |```json
        |{"tool":"add_event","title":"...","date":"yyyy-MM-dd","time":"HH:mm","durationMinutes":60}
        |```
        |```json
        |{"tool":"start_focus","minutes":25}
        |```
        |Emit a tool block only when the user clearly wants that action. Keep
        |the chat text before the block short and natural.
    """.trimMargin()
}
