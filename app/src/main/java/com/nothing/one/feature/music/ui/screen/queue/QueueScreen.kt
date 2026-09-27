package com.nothing.one.feature.music.ui.screen.queue

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.component.AlbumArtView
import com.nothing.one.feature.music.ui.component.DotMatrixText
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary
import com.nothing.one.feature.music.domain.model.formatDurationMs

@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val queue by playerViewModel.queue.collectAsState()
    val currentIndex by playerViewModel.currentIndex.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

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
            Text(
                text = "QUEUE",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.onBackground,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { playerViewModel.clearQueue() }) {
                Text(
                    text = "CLEAR",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            text = "${queue.size} TRACKS",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
            color = NothingTextTertiary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = DotMatrixFont),
                    color = NothingTextTertiary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { index, _ -> "${queue[index].id}-$index" }) { index, track ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { playerViewModel.jumpToQueueIndex(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlbumArtView(
                            artUri = track.albumArtUri?.toString(),
                            modifier = Modifier
                                .width(42.dp)
                                .height(42.dp),
                            cornerRadius = 8,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
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
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = formatDurationMs(track.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = NothingTextTertiary,
                            )
                        }
                        IconButton(
                            onClick = { playerViewModel.removeQueueItem(index) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Remove",
                                tint = NothingTextTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}