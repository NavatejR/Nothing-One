package com.nothing.one.feature.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.music.ui.component.DotMatrixHeaderTitle
import com.nothing.one.feature.music.ui.component.DotMatrixText
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary

@Composable
fun SettingsScreen(
    onNavigateToEqualizer: () -> Unit,
    onNavigateToFolderPicker: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Long?>(null) }

Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotMatrixHeaderTitle(text = "Settings")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionLabel("SOUND") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.GraphicEq,
                    title = "Equalizer",
                    subtitle = "Bass, treble, presets & virtualizer",
                    onClick = onNavigateToEqualizer,
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Nightlight,
                    title = "Sleep Timer",
                    subtitle = "Fade out and pause playback automatically",
                    onClick = {},
                )
            }

            item { SectionLabel("LIBRARY") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.FolderOpen,
                    title = "Manage Folders",
                    subtitle = "Choose which folders to scan",
                    onClick = onNavigateToFolderPicker,
                )
            }

            item { SectionLabel("PLAYLISTS (${playlists.size})") }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        text = "No playlists yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DotMatrixText(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${playlist.trackCount}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
                            color = NothingTextSecondary,
                        )
                        IconButton(onClick = { playlistToDelete = playlist.id }) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistRemove,
                                contentDescription = "Delete playlist",
                                tint = NothingTextTertiary,
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }

            item { SectionLabel("ABOUT") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "About Nothing Music",
                    subtitle = "Version 1.0.0",
                    onClick = { showAbout = true },
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated,
            title = {
                Text(
                    text = "NOTHING MUSIC",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotMatrixFont),
                )
            },
            text = {
                Text(
                    text = "A NothingOS-inspired music player.\n\n" +
                        "Supports MP3, AAC, FLAC, OGG, WAV, WMA, ALAC, AIFF & DSD.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NothingTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("CLOSE", style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont))
                }
            },
        )
    }

    playlistToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated,
            title = { Text("Delete playlist?") },
            text = { Text("This cannot be undone.", color = NothingTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(id)
                        playlistToDelete = null
                    }
                ) {
                    Text("DELETE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("CANCEL", style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = DotMatrixFont,
            letterSpacing = 2.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextTertiary,
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
                .padding(2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = NothingTextSecondary,
            )
        }
    }
}

@Composable
private fun NothingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceHighlight,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

