package com.nothing.one.feature.music.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAt: Long,
)

data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)