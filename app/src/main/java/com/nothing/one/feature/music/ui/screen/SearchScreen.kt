package com.nothing.one.feature.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.component.DotMatrixHeaderTitle
import com.nothing.one.feature.music.ui.component.DotMatrixText
import com.nothing.one.feature.music.ui.component.TrackListItem
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary

@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotMatrixHeaderTitle(text = "Search")
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
        }

        SearchField(
            query = query,
            onQueryChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (query.isBlank()) {
            QuickStats(
                trackCount = allTracks.size,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            Text(
                text = "${results.size} RESULTS",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
                color = NothingTextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = {
                            playerViewModel.playTrack(track, results)
                            onNavigateToNowPlaying()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = NothingTextSecondary,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search songs, artists, albums...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NothingTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear",
                    tint = NothingTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuickStats(
    trackCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = trackCount.toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "TRACKS READY",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
                color = NothingTextSecondary,
            )
            Text(
                text = if (trackCount == 0) {
                    "Grant storage permission and add folders to get started"
                } else {
                    "Start typing to search your library"
                },
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}