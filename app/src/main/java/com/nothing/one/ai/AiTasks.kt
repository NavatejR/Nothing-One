package com.nothing.one.ai

import com.nothing.one.data.model.Mood
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Result of an AI task: output plus whether the engine produced it. */
sealed interface AiResult<out T> {
    /** The engine answered. */
    data class Success<T>(val value: T) : AiResult<T>

    /** No engine present, or it failed — the caller should use [fallback]. */
    data class Unavailable<T>(val fallback: T) : AiResult<T>
}

/**
 * The AI features Journal ships, expressed as prompts against a local engine.
 * Every task has a deterministic fallback so the UI never blocks on AI.
 */
@Singleton
class AiTasks @Inject constructor(
    private val client: LocalAiClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** One-paragraph digest, max ~3 sentences, in the user's own words. */
    suspend fun summarize(text: String, fallback: String? = null): AiResult<String> {
        val body = text.trim()
        if (body.isEmpty()) return AiResult.Unavailable(fallback ?: "")
        val prompt = buildString {
            append("Summarize the following text in at most 3 short sentences. ")
            append("Keep the author's language. Reply with the summary only.\n\n")
            append(body.take(4000))
        }
        val out = client.generate(prompt, system = SYSTEM_BREVITY)
        return if (out != null) AiResult.Success(out) else AiResult.Unavailable(fallback ?: body.take(180))
    }

    /** Mood on the 1..5 scale, inferred from what the entry says. */
    suspend fun inferMood(text: String): AiResult<Mood?> {
        val body = text.trim()
        if (body.isEmpty()) return AiResult.Unavailable(null)
        val prompt = buildString {
            append(
                "How does the author of the following journal text feel? " +
                    "Answer with exactly one word from this list: "
            )
            append(Mood.entries.joinToString(", ") { it.label })
            append(".\n\n")
            append(body.take(2000))
        }
        val out = client.generate(prompt, system = SYSTEM_ONE_WORD)
            ?.trim()
            ?.trim('.', '!', '"', '\'')
            ?.uppercase()
        val mood: Mood? = out?.let { w ->
            Mood.fromKeyword(w)
                ?: Mood.entries.firstOrNull { it.name == w }
                ?: Mood.entries.firstOrNull { e -> w.contains(e.label) }
        }
        return if (mood != null) AiResult.Success(mood) else AiResult.Unavailable(null)
    }

    /** Up to [max] short lowercase tags for a note. */
    suspend fun suggestTags(text: String, max: Int = 4): AiResult<List<String>> {
        val body = text.trim()
        if (body.isEmpty()) return AiResult.Unavailable(emptyList())
        val prompt = buildString {
            append("Suggest up to ")
            append(max)
            append(" short lowercase topic tags (single words, no punctuation) ")
            append("for the following note. Reply with a JSON array of strings only.\n\n")
            append(body.take(2000))
        }
        val out = client.generate(prompt, system = SYSTEM_TAGS)
            ?: return AiResult.Unavailable(emptyList())
        return AiResult.Success(parseTagArray(out).take(max))
    }

    /** Restores a mood from raw text without calling the engine — used in fallbacks. */
    fun moodFromKeyword(raw: String): Mood? = Mood.fromKeyword(raw)

    /**
     * Lenient parser for the tags reply: models return bare arrays, fenced
     * arrays, or prose around an array. Finds the first [...] and parses it;
     * anything unparseable becomes no tags rather than an error.
     */
    internal fun parseTagArray(raw: String): List<String> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return try {
            json.decodeFromString(
                ListSerializer(String.serializer()),
                raw.substring(start, end + 1),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val SYSTEM_BREVITY = "You are a concise summarizer inside a personal journal app. Reply with the requested text only, no preamble, no quotes."
        const val SYSTEM_ONE_WORD = "You answer with exactly one word. No punctuation, no explanation."
        const val SYSTEM_TAGS = "You reply with a JSON array of short lowercase strings and nothing else."
    }
}
