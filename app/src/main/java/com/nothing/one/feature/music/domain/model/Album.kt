package com.nothing.one.feature.music.domain.model

data class Album(
    val name: String,
    val artist: String,
    val albumArtUri: String? = null,
    val trackCount: Int = 0,
    val year: Int = 0,
    val durationMs: Long = 0L,
)

data class Artist(
    val name: String,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val avatarUri: String? = null,
)

data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int = 0,
    val isSelected: Boolean = false,
)