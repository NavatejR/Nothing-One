package com.nothing.one.ui.screen.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nothing.one.AssistantViewModel
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.NothingRed

@Composable
fun OnboardingScreen(
    viewModel: AssistantViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        ShaderOrb(
            state = OrbState.IDLE,
            sizeDp = 180.dp,
            contentDescription = "Journal orb",
        )
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "JOURNAL", style = MaterialTheme.typography.headlineLarge)
        }
        DotGridDivider()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "A quiet place for quick notes and daily pages. Dictation, summaries and mood reading run entirely on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = if (micGranted) "MICROPHONE READY" else "MICROPHONE PERMISSION REQUESTED ON NEXT TAP",
            style = MaterialTheme.typography.labelSmall,
            color = if (micGranted) MaterialTheme.colorScheme.onSurfaceVariant else NothingRed,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (micGranted) {
                    viewModel.completeOnboarding()
                    onDone()
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = NothingRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (micGranted) "START" else "GRANT MICROPHONE",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
