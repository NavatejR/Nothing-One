package com.nothing.one.feature.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary

@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MUSIC",
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = DotMatrixFont),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Storage access is required to read your music files.",
            style = MaterialTheme.typography.bodyLarge,
            color = NothingTextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp),
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Choose which folders to scan in Settings",
            style = MaterialTheme.typography.bodySmall,
            color = NothingTextTertiary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(40.dp))
        TextButton(
            onClick = onRequestPermission,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 14.dp),
        ) {
            Text(
                text = "GRANT ACCESS",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
fun WelcomeOverlay(
    onContinue: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NOTHING MUSIC",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A minimal music player",
                style = MaterialTheme.typography.bodyLarge,
                color = NothingTextTertiary,
            )
            Spacer(modifier = Modifier.height(48.dp))
            TextButton(
                onClick = onContinue,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(24.dp))
                    .padding(horizontal = 40.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "START",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = DotMatrixFont),
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}