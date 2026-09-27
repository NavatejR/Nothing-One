package com.nothing.one.feature.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.component.TrackListItem
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val tracks by viewModel.tracksFor(playlistId).collectAsState()
    val allTracks by viewModel.tracks.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val playlist = playlists.firstOrNull { it.id == playlistId }
    val favSet = favoriteIds.toSet()

    var showAddMusic by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotMatrixFont),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTextSecondary,
                )
            }
            IconButton(onClick = { showRename = true }) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { showAddMusic = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Music", fontFamily = DotMatrixFont)
            }
            Button(
                onClick = {
                    if (tracks.isNotEmpty()) {
                        playerViewModel.playAll(tracks)
                        onNavigateToNowPlaying()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All", fontFamily = DotMatrixFont)
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tracks yet. Tap Add Music.",
                    color = NothingTextTertiary,
                    fontFamily = DotMatrixFont,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { _, track ->
                    TrackListItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = isPlaying,
                        isFavorite = track.id in favSet,
                        onClick = {
                            playerViewModel.playTrack(track, tracks)
                            onNavigateToNowPlaying()
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.removeTrackFromPlaylist(playlistId, track.id) },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove from playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "End of playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTextTertiary,
                        fontFamily = DotMatrixFont,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showAddMusic) {
        AddMusicSheet(
            tracks = allTracks,
            existingIds = tracks.map { it.id }.toSet(),
            onAdd = { ids ->
                if (ids.isNotEmpty()) viewModel.addToPlaylist(playlistId, ids)
            },
            onDismiss = { showAddMusic = false },
        )
    }

    if (showRename) {
        var name by remember(playlist?.name) { mutableStateOf(playlist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = {
                Text("Rename Playlist", fontFamily = DotMatrixFont)
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) viewModel.renamePlaylist(playlistId, trimmed)
                        showRename = false
                    },
                ) {
                    Text("Save", fontFamily = DotMatrixFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel", fontFamily = DotMatrixFont)
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text("Delete playlist?", fontFamily = DotMatrixFont)
            },
            text = {
                Text(
                    text = "\"${playlist?.name ?: ""}\" and its tracks will be removed.",
                    color = NothingTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlistId)
                        showDeleteConfirm = false
                        onBack()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.primary, fontFamily = DotMatrixFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = DotMatrixFont)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMusicSheet(
    tracks: List<Track>,
    existingIds: Set<Long>,
    onAdd: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val candidates = tracks.filter { it.id !in existingIds }

    val groups = remember(candidates) {
        candidates
            .groupBy { it.folderPath }
            .map { (path, folderTracks) ->
                FolderGroup(
                    folderPath = path,
                    folderName = path.substringAfterLast('/').ifBlank { path },
                    tracks = folderTracks.sortedWith(compareBy({ it.title.lowercase() })),
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            selected = emptySet()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Text(
            text = "Add Music",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotMatrixFont),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (candidates.isEmpty()) {
            Text(
                text = "All tracks are already in this playlist.",
                color = NothingTextTertiary,
                fontFamily = DotMatrixFont,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                groups.forEach { group ->
                    val folderIds = group.tracks.map { it.id }.toSet()
                    item(key = "folder:${group.folderPath}") {
                        val folderState = remember(group.folderPath, selected) {
                            val inside = selected.intersect(folderIds)
                            when {
                                inside.isEmpty() -> ToggleableState.Off
                                inside.size == folderIds.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                        }
                        AddFolderHeader(
                            name = group.folderName,
                            trackCount = group.tracks.size,
                            state = folderState,
                            onToggle = {
                                selected = if (folderState == ToggleableState.On) {
                                    selected - folderIds
                                } else {
                                    selected + folderIds
                                }
                            },
                        )
                    }
                    items(group.tracks, key = { it.id }) { track ->
                        AddTrackRow(
                            track = track,
                            isSelected = track.id in selected,
                            onToggle = {
                                selected = if (track.id in selected) selected - track.id
                                else selected + track.id
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onAdd(selected.toList())
                selected = emptySet()
                onDismiss()
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = if (selected.isEmpty()) "Add" else "Add ${selected.size}",
                fontFamily = DotMatrixFont,
            )
        }
    }
}

@Composable
private fun AddFolderHeader(
    name: String,
    trackCount: Int,
    state: ToggleableState,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceHighlight)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = onToggle,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$trackCount tracks",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddTrackRow(
    track: Track,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class FolderGroup(
    val folderPath: String,
    val folderName: String,
    val tracks: List<Track>,
)