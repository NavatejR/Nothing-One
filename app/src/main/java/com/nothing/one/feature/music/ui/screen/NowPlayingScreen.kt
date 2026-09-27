package com.nothing.one.feature.music.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.component.AlbumArtView
import com.nothing.one.feature.music.ui.component.DotMatrixText
import com.nothing.one.feature.music.ui.component.NothingLinearProgress
import com.nothing.one.feature.music.ui.screen.lyrics.LyricsBottomSheet
import com.nothing.one.feature.music.service.toTrack
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.domain.model.formatDurationMs
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToQueue: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val track by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val position by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val isShuffled by playerViewModel.isShuffled.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val favoriteIds by libraryViewModel.favoriteIds.collectAsState()

    val isFavorite = track?.id in favoriteIds.toSet()

    var showLyrics by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DotMatrixText(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showLyrics = true }) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistPlay,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Spinning vinyl disc
            VinylDisc(
                artUri = track?.albumArtUri?.toString(),
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Track info
            DotMatrixText(
                text = track?.title ?: "No track",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = track?.artist ?: " ",
                style = MaterialTheme.typography.bodyLarge,
                color = NothingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = track?.album ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (isPlaying) {
                Spacer(modifier = Modifier.height(18.dp))
                WaveBars()
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Progress
            NothingLinearProgress(
                progress = if (duration > 0) position.toFloat() / duration else 0f,
                height = 3,
                modifier = Modifier.fillMaxWidth(),
                onSeek = {},
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDurationMs(position),
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingTextSecondary,
                )
                Text(
                    text = formatDurationMs(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingTextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { playerViewModel.previous() }) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = { playerViewModel.playPause() },
                )
                IconButton(onClick = { playerViewModel.next() }) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = { playerViewModel.toggleRepeat() }) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Outlined.RepeatOne
                        } else {
                            Icons.Outlined.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { track?.let { libraryViewModel.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CntBadge(text = "${queue.size}")
                IconButton(onClick = onNavigateToQueue) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistPlay,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }

    if (showLyrics) {
        LyricsBottomSheet(
            viewModel = lyricsViewModel,
            track = track,
            positionMs = position,
            onDismiss = { showLyrics = false },
        )
    }
}

private val VinylGrooveDark = Color(0xFF151515)
private val VinylGrooveLight = Color(0xFF232323)

@Composable
private fun VinylDisc(
    artUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "discRotation",
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = if (isPlaying) rotation else 0f
        },
        contentAlignment = Alignment.Center,
    ) {
        // Disc body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(com.nothing.one.feature.music.ui.theme.NothingSurface)
                .border(1.dp, VinylGrooveDark, CircleShape)
        ) {
            // Vinyl grooves — dense concentric rings reaching the edge
            for (i in 38 downTo 4 step 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((i * 9).dp)
                        .clip(CircleShape)
                        .background(
                            if (i % 8 == 0) VinylGrooveLight else VinylGrooveDark
                        )
                )
            }
        }

        // Center album art — a crisp circle, never an ellipse
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(3.dp)
        ) {
            AlbumArtView(
                artUri = artUri,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }

        // Spindle hole
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceHighlight)
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(44.dp),
            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.background,
        )
    }
}

@Composable
private fun CntBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveBars() {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(4) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 460 + i * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}