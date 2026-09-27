package com.nothing.one.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.ai.EngineState
import com.nothing.one.ai.OnDeviceAiClient
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixBadge
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.SectionLabel
import com.nothing.one.ui.theme.NothingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "SETTINGS", style = MaterialTheme.typography.headlineSmall)
        }
        DotGridDivider()

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))

            // Engine section — the model ships with the app; nothing to configure.
            SectionLabel("LOCAL ENGINE")
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EngineBadge(engineState)
                DotMatrixBadge(
                    text = OnDeviceAiClient.MODEL_LABEL,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (engineState) {
                    EngineState.READY ->
                        "Bundled engine loaded — every AI feature runs on this device. No network, ever."
                    EngineState.LOADING ->
                        "Bundled engine warms up on first use; the first answer takes a few seconds."
                    EngineState.UNAVAILABLE ->
                        "Bundled engine files are missing from this build. AI features fall back to manual mode."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (engineState == EngineState.UNAVAILABLE) NothingRed
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            // Dictation section
            SectionLabel("DICTATION")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Voice goes straight into the bundled whisper model in this app's own memory. When the model is absent, the platform recognizer takes over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            // Motion section
            SectionLabel("MOTION")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduced motion", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Freezes the orb on a single frame",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings?.reducedMotion == true,
                    onCheckedChange = { viewModel.setReducedMotion(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = NothingRed),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Data section
            SectionLabel("DATA")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Everything lives in this device's app storage. Export writes a Markdown file you choose where to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { exportLauncher.launch("journal-export.md") }) {
                Text("EXPORT MARKDOWN", style = MaterialTheme.typography.labelMedium)
            }
            exportState?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "JOURNAL 2.0 — NOTHING OS EDITION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EngineBadge(state: EngineState) {
    val (label, active) = when (state) {
        EngineState.READY -> "READY" to true
        EngineState.LOADING -> "LOADING" to false
        EngineState.UNAVAILABLE -> "UNAVAILABLE" to false
    }
    DotMatrixBadge(
        text = label,
        color = if (active) NothingRed else MaterialTheme.colorScheme.surfaceVariant,
        textColor = if (active) androidx.compose.ui.graphics.Color.Black
        else MaterialTheme.colorScheme.onSurface,
    )
}