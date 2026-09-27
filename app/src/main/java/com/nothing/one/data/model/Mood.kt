package com.nothing.one.data.model

import androidx.compose.ui.graphics.Color
import com.nothing.one.ui.theme.Mood1Color
import com.nothing.one.ui.theme.Mood2Color
import com.nothing.one.ui.theme.Mood3Color
import com.nothing.one.ui.theme.Mood4Color
import com.nothing.one.ui.theme.Mood5Color

/**
 * Five-step mood scale. One hue per step, deliberately restrained so the
 * Nothing red stays the only saturated accent in the app.
 */
enum class Mood(val value: Int, val label: String, val color: Color) {
    AWFUL(1, "AWFUL", Mood1Color),
    LOW(2, "LOW", Mood2Color),
    OKAY(3, "OKAY", Mood3Color),
    GOOD(4, "GOOD", Mood4Color),
    GREAT(5, "GREAT", Mood5Color);

    companion object {
        fun fromValue(v: Int?): Mood? = entries.firstOrNull { it.value == v }

        /**
         * Maps a free-text word (from the user or the local AI) onto the
         * scale. Used by mood inference and by manual quick-pick search.
         */
        fun fromKeyword(raw: String): Mood? {
            val w = raw.trim().lowercase()
            return when {
                w in setOf("awful", "terrible", "horrible", "angry", "exhausted", "drained", "awful day") -> AWFUL
                w in setOf("low", "sad", "down", "tired", "meh", "anxious", "stressed", "lonely") -> LOW
                w in setOf("okay", "ok", "fine", "alright", "average", "neutral", "busy") -> OKAY
                w in setOf("good", "calm", "steady", "productive", "hopeful", "grateful") -> GOOD
                w in setOf("great", "happy", "amazing", "excited", "joyful", "proud", "wonderful") -> GREAT
                else -> null
            }
        }
    }
}
