package com.nothing.one.feature.music.ui.screen.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nothing.one.feature.music.domain.model.Lyrics
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.ui.screen.LyricsViewModel
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    viewModel: LyricsViewModel,
    track: Track?,
    positionMs: Long,
    onDismiss: () -> Unit,
) {
    val lyrics by viewModel.lyrics.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(track?.id) {
        viewModel.loadForTrack(track)
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated,
        dragHandle = {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.width(48.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "LYRICS".let { it },
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = DotMatrixFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "LOADING...",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
                            color = NothingTextTertiary,
                        )
                    }
                }
                lyrics.lines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "NO LYRICS FOUND",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
                            color = NothingTextTertiary,
                        )
                    }
                }
                else -> {
                    // Find the active lyric line by current position
                    val activeIndex = remember(positionMs, lyrics.lines.size) {
                        lyrics.lines.indexOfLast { it.timestampMs <= positionMs }
                            .coerceAtLeast(0)
                    }
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isCurrent = index == activeIndex
                            Text(
                                text = if (line.text.isBlank()) "\u00A0" else line.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = if (isCurrent) DotMatrixFont else androidx.compose.ui.text.font.FontFamily.SansSerif,
                                ),
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    NothingTextSecondary
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }

                    LaunchedEffect(activeIndex) {
                        if (activeIndex > 0) {
                            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                        }
                    }
                }
            }
        }
    }
}