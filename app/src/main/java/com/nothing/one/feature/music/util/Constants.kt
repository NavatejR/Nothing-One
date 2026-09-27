package com.nothing.one.feature.music.util

object Constants {
    const val PACKAGE_NAME = "com.nothing.one.feature.music"

    const val DATABASE_NAME = "nothing_music.db"

    const val CHANNEL_PLAYBACK_ID = "playback"
    const val CHANNEL_PLAYBACK_NAME = "Playback"
    const val NOTIFICATION_ID = 1

    const val ACTION_STOP_SERVICE = "com.nothing.one.feature.music.ACTION_STOP_SERVICE"
    const val ACTION_SLEEP_TIMER_DONE = "com.nothing.one.feature.music.ACTION_SLEEP_TIMER_DONE"

    const val EQUALIZER_BAND_COUNT = 5
    const val EQUALIZER_MIN_GAIN_MB = -1500
    const val EQUALIZER_MAX_GAIN_MB = 1500

    const val DEFAULT_FADE_OUT_MS = 30_000L

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "aac", "m4a", "mp4", "flac", "ogg", "opus",
        "wav", "alac", "aiff", "aif", "wma", "dsf", "dff",
    )

    val SUPPORTED_MIME_TYPES = setOf(
        "audio/mpeg", "audio/aac", "audio/mp4", "audio/flac",
        "audio/ogg", "audio/opus", "audio/wav", "audio/vnd.wave",
        "audio/aiff", "audio/x-aiff", "audio/x-ms-wma", "audio/x-alac",
        "audio/x-dsf", "audio/x-dff", "audio/x-hx-aac-adts",
    )
}