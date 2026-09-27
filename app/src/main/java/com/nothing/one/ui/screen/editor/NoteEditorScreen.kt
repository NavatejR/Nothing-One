package com.nothing.one.ui.screen.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.speech.AssistantState
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixBadge
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.DictationMenu
import com.nothing.one.ui.markdown.Markdown
import com.nothing.one.ui.markdown.MarkdownColors
import com.nothing.one.ui.markdown.livePreview
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.NothingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    templateId: String?,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(noteId) { viewModel.load(noteId) }
    LaunchedEffect(templateId) {
        templateId?.let { viewModel.applyTemplate(com.nothing.one.data.NoteTemplates.byId(it)) }
    }

    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val aiMessage by viewModel.aiMessage.collectAsState()
    val dictationState by viewModel.dictationState.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()

    // Body as TextFieldValue so toolbar edits can move the cursor precisely.
    var bodyField by remember { mutableStateOf(TextFieldValue("")) }
    var pendingFormat by remember { mutableStateOf(false) }
    var lastFormatAt by remember { mutableStateOf(0L) }

    LaunchedEffect(body) {
        if (pendingFormat) {
            pendingFormat = false
        } else if (bodyField.text != body) {
            // External change (load, template, dictation): put the cursor at
            // the end, which is where appended dictation lands anyway.
            bodyField = TextFieldValue(body, TextRange(body.length))
        }
    }

    val dictating = dictationState == AssistantState.LISTENING || dictationState == AssistantState.THINKING
    BackHandler(enabled = dictating) { viewModel.stopDictation() }

    fun applyEdit(edit: Markdown.Edit) {
        pendingFormat = true
        bodyField = TextFieldValue(edit.text, TextRange(edit.cursor.coerceIn(0, edit.text.length)))
        viewModel.body.value = edit.text
        viewModel.onTextChanged()
    }

    /** Debounced format press — a fast double-tap must not double-wrap. */
    fun formatOnce(action: () -> Markdown.Edit) {
        val now = System.currentTimeMillis()
        if (now - lastFormatAt < 250) return
        lastFormatAt = now
        applyEdit(action())
    }

    fun insertAtCursor(text: String) {
        val sel = bodyField.selection
        val at = if (sel.collapsed) sel.start else sel.end
        val sb = StringBuilder(bodyField.text)
        sb.insert(at.coerceAtMost(sb.length), text)
        applyEdit(Markdown.Edit(sb.toString(), at + text.length))
    }

    // Dictation results land at the cursor once the transcript settles —
    // finalResult fills only after the tail drain finishes, so the last
    // utterance spoken before stop is included.
    val dictationFinal by viewModel.finalResult.collectAsState()
    LaunchedEffect(dictationFinal) {
        if (dictationFinal.isNotBlank()) {
            insertAtCursor(if (bodyField.text.isEmpty()) dictationFinal else " $dictationFinal")
            viewModel.clearFinalResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (dictating) viewModel.stopDictation()
                onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.togglePinned() }) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if (pinned) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.delete(onBack) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DotGridDivider()

        FormatToolbar(
            onBold = { formatOnce { Markdown.toggleWrap(bodyField.text, bodyField.selection.min, "**") } },
            onItalic = { formatOnce { Markdown.toggleWrap(bodyField.text, bodyField.selection.min, "*") } },
            onCode = { formatOnce { Markdown.toggleWrap(bodyField.text, bodyField.selection.min, "`") } },
            onHeading = { formatOnce { Markdown.toggleLinePrefix(bodyField.text, bodyField.selection.min, Markdown.PrefixKind.HEADING) } },
            onQuote = { formatOnce { Markdown.toggleLinePrefix(bodyField.text, bodyField.selection.min, Markdown.PrefixKind.QUOTE) } },
            onBullet = { formatOnce { Markdown.toggleLinePrefix(bodyField.text, bodyField.selection.min, Markdown.PrefixKind.BULLET) } },
            onCheck = { formatOnce { Markdown.toggleLinePrefix(bodyField.text, bodyField.selection.min, Markdown.PrefixKind.CHECK) } },
        )
        DotGridDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    viewModel.title.value = it
                    viewModel.onTextChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Title", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = NothingRed,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                singleLine = true,
            )

            OutlinedTextField(
                value = bodyField,
                onValueChange = { tfv ->
                    bodyField = tfv
                    viewModel.body.value = tfv.text
                    viewModel.onTextChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Start writing — or tap the orb to dictate",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                // Obsidian-style live preview: markers hide and text renders
                // actively on every line except the cursor's, which keeps its
                // raw colorized Markdown. Colors are hoisted because the
                // transform lambda is not composable.
                visualTransformation = run {
                    val mdColors = MarkdownColors.default()
                    livePreview(mdColors) { bodyField.selection.min }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = NothingRed,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                minLines = 8,
            )

            Spacer(Modifier.height(8.dp))

            // Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tags.forEach { tag ->
                    DotMatrixBadge(
                        text = tag,
                        modifier = Modifier.clickable { viewModel.removeTag(tag) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            var tagInput by remember { mutableStateOf("") }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Add tag…", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NothingRed,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = NothingRed,
                ),
                singleLine = true,
                trailingIcon = {
                    if (tagInput.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.addTag(tagInput)
                            tagInput = ""
                        }) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = "Add tag",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            // AI actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { viewModel.summarize() }, enabled = !aiBusy) {
                    DotMatrixText(
                        text = if (aiBusy) "…" else "SUMMARIZE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = { viewModel.suggestTags() }, enabled = !aiBusy) {
                    DotMatrixText(
                        text = "TAGS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            aiMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(40.dp))
        }

        // Bottom dictation bar: mini orb + live status.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShaderOrb(
                state = when (dictationState) {
                    AssistantState.LISTENING -> OrbState.LISTENING
                    AssistantState.THINKING -> OrbState.THINKING
                    AssistantState.SPEAKING -> OrbState.SPEAKING
                    AssistantState.SLEEPING -> OrbState.SLEEPING
                    AssistantState.IDLE -> OrbState.IDLE
                },
                sizeDp = 44.dp,
                pinnedInput = micLevel,
                reduceMotion = false,
                contentDescription = "Dictate",
                modifier = Modifier.clickable {
                    if (dictating) viewModel.stopDictation() else viewModel.startDictation()
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    dictationState == AssistantState.LISTENING -> "LISTENING — TAP ORB TO STOP"
                    dictationState == AssistantState.THINKING -> "TRANSCRIBING…"
                    else -> "TAP THE ORB TO DICTATE INTO THIS NOTE"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Full-screen dictation menu: orb grows, transcript under it.
    if (dictating) {
        DictationMenu(
            orbState = if (dictationState == AssistantState.THINKING) OrbState.THINKING
            else OrbState.LISTENING,
            micLevel = micLevel,
            partial = partial,
            idleHint = "SPEAK NOW",
            restSize = 180.dp,
            menuSize = 280.dp,
            onStop = { viewModel.stopDictation() },
            onCancel = { viewModel.cancelDictation() },
        )
    }
}

/** Dot-matrix formatting toolbar: wraps and prefixes, cursor-aware. */
@Composable
private fun FormatToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onCode: () -> Unit,
    onHeading: () -> Unit,
    onQuote: () -> Unit,
    onBullet: () -> Unit,
    onCheck: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatButton("B", onBold, bold = true)
        FormatButton("I", onItalic, italic = true)
        FormatButton("</>", onCode)
        FormatButton("H", onHeading)
        FormatButton("❝", onQuote)
        FormatButton("•", onBullet)
        FormatButton("☑", onCheck)
    }
}

@Composable
private fun FormatButton(
    label: String,
    onClick: () -> Unit,
    bold: Boolean = false,
    italic: Boolean = false,
) {
    Box(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = com.nothing.one.ui.theme.DotMatrixFont,
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Normal,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
