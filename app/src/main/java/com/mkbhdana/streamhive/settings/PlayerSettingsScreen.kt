package com.mkbhdana.streamhive.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mkbhdana.streamhive.player.mpv.PlayerEngine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState
    var editingConfig by remember { mutableStateOf<EditingConfig?>(null) }

    editingConfig?.let { target ->
        MpvConfigEditorDialog(
            fileName = target.fileName,
            initialText = when (target) {
                EditingConfig.MpvConf -> state.mpvConfText
                EditingConfig.InputConf -> state.mpvInputConfText
            },
            onSave = { text ->
                when (target) {
                    EditingConfig.MpvConf -> viewModel.setMpvConfText(text)
                    EditingConfig.InputConf -> viewModel.setMpvInputConfText(text)
                }
                editingConfig = null
            },
            onDismiss = { editingConfig = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard {
                    var engineExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Default Player",
                        subtitle = when (state.preferredEngine) {
                            PlayerEngine.EXO_PLAYER -> "ExoPlayer (Media3)"
                            PlayerEngine.MPV -> "MPV Player"
                            PlayerEngine.EXTERNAL -> "External (Open with)"
                        },
                        expanded = engineExpanded,
                        onToggle = { engineExpanded = !engineExpanded },
                        icon = Icons.Default.SmartDisplay
                    ) {
                        DropdownMenuItem(
                            text = { Text("ExoPlayer (Media3)") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.EXO_PLAYER); engineExpanded = false },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("MPV Player") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.MPV); engineExpanded = false },
                            leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(20.dp)) },
                            enabled = state.isMpvAvailable
                        )
                        DropdownMenuItem(
                            text = { Text("External (Open with)") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.EXTERNAL); engineExpanded = false },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(20.dp)) }
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var exoDecoderExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "ExoPlayer Decoder",
                        subtitle = when (state.exoDecoder) {
                            "hw" -> "Hardware (HW)"; "sw" -> "Software (SW)"; "hw+" -> "Hardware+ (HW+)"
                            else -> state.exoDecoder
                        },
                        expanded = exoDecoderExpanded,
                        onToggle = { exoDecoderExpanded = !exoDecoderExpanded },
                        icon = Icons.Default.Memory
                    ) {
                        listOf("hw" to "Hardware (HW)", "hw+" to "Hardware+ (HW+)", "sw" to "Software (SW)").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.setExoDecoder(k); exoDecoderExpanded = false })
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var mpvDecoderExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "MPV Decoder",
                        subtitle = when (state.mpvDecoder) {
                            "hw" -> "Hardware (HW)"; "sw" -> "Software (SW)"; "hw+" -> "Hardware+ (HW+)"; "auto" -> "Auto"
                            else -> state.mpvDecoder
                        },
                        expanded = mpvDecoderExpanded,
                        onToggle = { mpvDecoderExpanded = !mpvDecoderExpanded },
                        icon = Icons.Default.Memory
                    ) {
                        listOf("hw" to "Hardware (HW)", "hw+" to "Hardware+ (HW+)", "sw" to "Software (SW)", "auto" to "Auto").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.setMpvDecoder(k); mpvDecoderExpanded = false })
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        "Map DV7 to HEVC",
                        "Dolby Vision Profile 7 to HEVC fallback for unsupported devices",
                        Icons.Default.Memory,
                        state.mapDv7ToHevc,
                        viewModel::setMapDv7ToHevc,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Tunnelled Playback",
                        "Experimental hardware path; applied only with HW decoder",
                        Icons.Default.Speed,
                        state.tunneledPlaybackEnabled,
                        viewModel::setTunneledPlaybackEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var resizeExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Default Resize Mode",
                        subtitle = when (state.defaultResizeMode) {
                            "fit" -> "Fit"; "fill" -> "Fill"; "zoom" -> "Zoom"
                            "16:9" -> "16:9"; "4:3" -> "4:3"; else -> state.defaultResizeMode
                        },
                        expanded = resizeExpanded,
                        onToggle = { resizeExpanded = !resizeExpanded },
                        icon = Icons.Default.AspectRatio
                    ) {
                        listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom", "16:9" to "16:9", "4:3" to "4:3").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.setDefaultResizeMode(k); resizeExpanded = false })
                        }
                    }
                }
            }



            item {
                SettingsSectionHeader(Icons.Default.Videocam, "MPV Engine")
            }

            item {
                SettingsCard {
                    var profileExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "MPV Profile",
                        subtitle = MpvOptions.profileLabel(state.mpvProfile),
                        expanded = profileExpanded,
                        onToggle = { profileExpanded = !profileExpanded },
                        icon = Icons.Default.Tune
                    ) {
                        MpvOptions.profiles.forEach { (k, v) ->
                            DropdownMenuItem(
                                text = { Text(v) },
                                onClick = { viewModel.setMpvProfile(k); profileExpanded = false },
                                trailingIcon = if (state.mpvProfile == k) { { Icon(Icons.Default.Check, null) } } else null
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        "Use gpu-next",
                        "A new rendering backend (applies on next playback)",
                        Icons.Default.AutoAwesome,
                        state.mpvGpuNext,
                        viewModel::setMpvGpuNext,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        "Use Vulkan (Experimental)",
                        "Enable Vulkan rendering on supported devices (may improve performance)",
                        Icons.Default.Bolt,
                        state.mpvUseVulkan,
                        viewModel::setMpvUseVulkan,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    var debandExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Debanding",
                        subtitle = MpvOptions.debandingLabel(state.mpvDebanding),
                        expanded = debandExpanded,
                        onToggle = { debandExpanded = !debandExpanded },
                        icon = Icons.Default.Gradient
                    ) {
                        MpvOptions.debanding.forEach { (k, v) ->
                            DropdownMenuItem(
                                text = { Text(v) },
                                onClick = { viewModel.setMpvDebanding(k); debandExpanded = false },
                                trailingIcon = if (state.mpvDebanding == k) { { Icon(Icons.Default.Check, null) } } else null
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        "Use YUV420P pixel format",
                        "May fix black screens on some video codecs, can also improve performance at the cost of quality",
                        Icons.Default.FilterBAndW,
                        state.mpvUseYuv420p,
                        viewModel::setMpvUseYuv420p,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsActionItem(
                        title = "Reset to defaults",
                        subtitle = "Restore the MPV engine settings above (mpv.conf is kept)",
                        icon = Icons.Default.RestartAlt,
                        onClick = { viewModel.resetMpvEngineSettings() }
                    )
                }
            }

            item {
                SettingsSectionHeader(Icons.Default.Code, "MPV Configuration")
            }

            item {
                SettingsCard {
                    SettingsActionItem(
                        title = "Edit mpv.conf",
                        subtitle = if (state.mpvConfText.isBlank()) "Tap to edit configuration" else "Custom configuration active",
                        icon = Icons.Default.Description,
                        onClick = { editingConfig = EditingConfig.MpvConf }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsActionItem(
                        title = "Edit input.conf",
                        subtitle = if (state.mpvInputConfText.isBlank()) "Tap to edit configuration" else "Custom configuration active",
                        icon = Icons.Default.Keyboard,
                        onClick = { editingConfig = EditingConfig.InputConf }
                    )
                }
            }

            item {
                SettingsSectionHeader(Icons.Default.Tune, "Source Priority")
            }

            item {
                SettingsCard {
                    SourcePriorityEditor(
                        title = "Resolution Priority",
                        subtitle = "Choose the preferred resolution sequence for meta screen files.",
                        options = SourcePriorityOptions.resolutions,
                        selected = state.sourcePriorityResolutions,
                        onSelectedChange = viewModel::setSourcePriorityResolutions
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SourcePriorityEditor(
                        title = "Video Format Priority",
                        subtitle = "Ranks Dolby Vision, HDR, SDR, and unknown format tags.",
                        options = SourcePriorityOptions.videoFormats,
                        selected = state.sourcePriorityVideoFormats,
                        onSelectedChange = viewModel::setSourcePriorityVideoFormats
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SourcePriorityEditor(
                        title = "Decoder Priority",
                        subtitle = "Ranks codec tags found in file titles, such as HEVC or H264.",
                        options = SourcePriorityOptions.decoders,
                        selected = state.sourcePriorityDecoders,
                        onSelectedChange = viewModel::setSourcePriorityDecoders
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SourcePriorityEditor(
                        title = "Container Priority",
                        subtitle = "Ranks filename formats such as MKV, MP4, 3GP, or WEBM.",
                        options = SourcePriorityOptions.containers,
                        selected = state.sourcePriorityContainers,
                        onSelectedChange = viewModel::setSourcePriorityContainers
                    )
                }
            }
        }
    }
}

/** The two mpv config files the user can edit in-app. */
private enum class EditingConfig(val fileName: String) {
    MpvConf("mpv.conf"),
    InputConf("input.conf")
}

/**
 * Full-screen monospace editor for mpv.conf / input.conf. The text is stored in
 * preferences and written into mpv's config-dir right before the engine starts,
 * so edits take effect on the next playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpvConfigEditorDialog(
    fileName: String,
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(fileName) { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(fileName) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Discard changes")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSave(text) }) { Text("Save") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "One option per line, e.g. \"scale=ewa_lanczos\". Options set here override the app's own settings. Leave empty to remove the file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    placeholder = { Text("# $fileName") }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcePriorityEditor(
    title: String,
    subtitle: String,
    options: List<SourcePriorityOption>,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    val sanitizedSelected = SourcePriorityOptions.sanitizeOrder(selected, options)
    val availableOptions = options.filter { option -> option.id !in sanitizedSelected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { addMenuExpanded = true },
                    enabled = availableOptions.isNotEmpty()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add priority chip")
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false }
                ) {
                    availableOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSelectedChange(sanitizedSelected + option.id)
                                addMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sanitizedSelected.isEmpty()) {
            Text(
                text = "No priority set. All available files will be listed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sanitizedSelected.forEachIndexed { index, value ->
                    PriorityChip(
                        index = index,
                        label = SourcePriorityOptions.labelFor(options, value),
                        canMoveUp = index > 0,
                        canMoveDown = index < sanitizedSelected.lastIndex,
                        onMoveUp = {
                            val updated = sanitizedSelected.toMutableList()
                            val item = updated.removeAt(index)
                            updated.add(index - 1, item)
                            onSelectedChange(updated)
                        },
                        onMoveDown = {
                            val updated = sanitizedSelected.toMutableList()
                            val item = updated.removeAt(index)
                            updated.add(index + 1, item)
                            onSelectedChange(updated)
                        },
                        onRemove = {
                            onSelectedChange(sanitizedSelected - value)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onSelectedChange(options.map { it.id }) }
            ) {
                Text("Use Suggested")
            }
            if (sanitizedSelected.isNotEmpty()) {
                TextButton(onClick = { onSelectedChange(emptyList()) }) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(
    index: Int,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}. $label",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}
