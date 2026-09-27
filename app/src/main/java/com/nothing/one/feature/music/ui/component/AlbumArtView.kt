package com.nothing.one.feature.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage

@Composable
fun AlbumArtView(
    artUri: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 0,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
    ) {
        if (artUri.isNullOrBlank()) {
            NothingArtFallback(Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                loading = {
                    NothingArtFallback(Modifier.fillMaxSize())
                },
                error = {
                    NothingArtFallback(Modifier.fillMaxSize())
                },
            )
        }
    }
}

@Composable
fun NothingArtFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}