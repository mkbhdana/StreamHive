package com.mkbhdana.streamhive.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio & Subtitles") },
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
                    var audioLangExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Preferred Audio Language",
                        subtitle = "Select preferred track language",
                        expanded = audioLangExpanded,
                        onToggle = { audioLangExpanded = !audioLangExpanded },
                        icon = Icons.Default.Audiotrack
                    ) {
                        val extendedLanguages = listOf(
                            "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
                            "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
                            "ru" to "Russian", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil",
                            "te" to "Telugu", "ml" to "Malayalam", "gu" to "Gujarati", "ar" to "Arabic", 
                            "pt" to "Portuguese", "tr" to "Turkish", "th" to "Thai", "vi" to "Vietnamese", 
                            "id" to "Indonesian"
                        )
                        val audioLanguages = listOf("original" to "Original") + extendedLanguages
                        audioLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { viewModel.setPreferredAudioLanguage(code); audioLangExpanded = false },
                                trailingIcon = if (state.preferredAudioLanguage == code) { { Icon(Icons.Default.Check, null) } } else null
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Volume Boost",
                        "Let the volume gesture go above 100%, up to 200%. Loud levels may distort audio or damage hearing.",
                        Icons.AutoMirrored.Filled.VolumeUp,
                        state.volumeBoostEnabled,
                        viewModel::setVolumeBoostEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var subLangExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Preferred Subtitle Language",
                        subtitle = "Select preferred subtitle track",
                        expanded = subLangExpanded,
                        onToggle = { subLangExpanded = !subLangExpanded },
                        icon = Icons.Default.Subtitles
                    ) {
                        val extendedLanguages = listOf(
                            "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
                            "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
                            "ru" to "Russian", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil",
                            "te" to "Telugu", "ml" to "Malayalam", "gu" to "Gujarati", "ar" to "Arabic", 
                            "pt" to "Portuguese", "tr" to "Turkish", "th" to "Thai", "vi" to "Vietnamese", 
                            "id" to "Indonesian"
                        )
                        val subLanguages = listOf("none" to "None (Disabled)") + extendedLanguages
                        subLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { viewModel.setPreferredSubtitleLanguage(code); subLangExpanded = false },
                                trailingIcon = if (state.preferredSubtitleLanguage == code) { { Icon(Icons.Default.Check, null) } } else null
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    var showExclusionDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showExclusionDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SpeakerNotesOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Excluded Languages", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (state.subtitleExcludeLanguages.isEmpty()) "None" else state.subtitleExcludeLanguages.joinToString(", ") + " excluded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (showExclusionDialog) {
                        AlertDialog(
                            onDismissRequest = { showExclusionDialog = false },
                            title = { Text("Exclude Languages") },
                            text = {
                                val languages = listOf(
                                    "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
                                    "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
                                    "ru" to "Russian", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil",
                                    "te" to "Telugu", "ml" to "Malayalam", "gu" to "Gujarati", "ar" to "Arabic", 
                                    "pt" to "Portuguese", "tr" to "Turkish", "th" to "Thai", "vi" to "Vietnamese", 
                                    "id" to "Indonesian"
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(languages.size) { index ->
                                        val (code, name) = languages[index]
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                            val newSet = if (state.subtitleExcludeLanguages.contains(code)) {
                                                state.subtitleExcludeLanguages - code
                                            } else {
                                                state.subtitleExcludeLanguages + code
                                            }
                                            viewModel.setSubtitleExcludeLanguages(newSet)
                                        }) {
                                            Checkbox(checked = state.subtitleExcludeLanguages.contains(code), onCheckedChange = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(name)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showExclusionDialog = false }) { Text("Done") } }
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Enable LibASS (MPV Only)",
                        "Render ASS/SSA subtitles with native styling",
                        Icons.Default.ClosedCaption,
                        state.libassSubtitlesEnabled,
                        viewModel::setLibassSubtitlesEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    if (state.libassSubtitlesEnabled) {
                        SettingsSwitchItem(
                            "Override ASS Styles",
                            "Force custom font size and colors on ASS subtitles",
                            Icons.Default.FormatPaint,
                            state.mpvOverrideAssSubtitleStyles,
                            viewModel::setMpvOverrideAssSubtitleStyles,
                            hapticsEnabled = state.hapticFeedbackEnabled
                        )
                    }
                }
            }

            item { SettingsSectionHeader(Icons.Default.FormatSize, "Subtitle Appearance") }

            item {
                var selectedPlayerTab by remember { mutableStateOf(0) }
                val isExo = selectedPlayerTab == 0
                val currentFontSize = if (isExo) state.exoSubtitleFontSize else state.mpvSubtitleFontSize
                val setFontSize = if (isExo) viewModel::setExoSubtitleFontSize else viewModel::setMpvSubtitleFontSize
                val currentPosition = if (isExo) state.exoSubtitlePosition else state.mpvSubtitlePosition
                val setPosition = if (isExo) viewModel::setExoSubtitlePosition else viewModel::setMpvSubtitlePosition
                val currentBgOpacity = if (isExo) state.exoSubtitleBgOpacity else state.mpvSubtitleBgOpacity
                val setBgOpacity = if (isExo) viewModel::setExoSubtitleBgOpacity else viewModel::setMpvSubtitleBgOpacity
                val currentEdgeType = if (isExo) state.exoSubtitleEdgeType else state.mpvSubtitleEdgeType
                val setEdgeType = if (isExo) viewModel::setExoSubtitleEdgeType else viewModel::setMpvSubtitleEdgeType
                val currentColor = if (isExo) state.exoSubtitleColor else state.mpvSubtitleColor
                val setColor = if (isExo) viewModel::setExoSubtitleColor else viewModel::setMpvSubtitleColor

                SettingsCard {
                    TabRow(
                        selectedTabIndex = selectedPlayerTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    ) {
                        Tab(selected = isExo, onClick = { selectedPlayerTab = 0 }, text = { Text("ExoPlayer") })
                        Tab(selected = !isExo, onClick = { selectedPlayerTab = 1 }, text = { Text("MPV") })
                    }
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Font Size", style = MaterialTheme.typography.bodyMedium)
                            Text("${currentFontSize}px", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        HapticSlider(
                            value = currentFontSize.toFloat(),
                            onValueChange = { setFontSize(it.roundToInt()) },
                            valueRange = 10f..48f,
                            steps = 37,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            hapticsEnabled = state.hapticFeedbackEnabled
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    if (!isExo) {
                        // Subtitle Scale
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtitle Scale", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.1f", state.subtitleScale)}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            HapticSlider(
                                value = state.subtitleScale,
                                onValueChange = { viewModel.setSubtitleScale((it * 10).roundToInt() / 10f) },
                                valueRange = 0.5f..3.0f,
                                steps = 24,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                hapticsEnabled = state.hapticFeedbackEnabled
                            )
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                        // Bold & Italic toggles
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Style", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.subtitleBold,
                                    onClick = { viewModel.setSubtitleBold(!state.subtitleBold) },
                                    label = { Text("Bold", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.FormatBold, "Bold", modifier = Modifier.size(18.dp)) }
                                )
                                FilterChip(
                                    selected = state.subtitleItalic,
                                    onClick = { viewModel.setSubtitleItalic(!state.subtitleItalic) },
                                    label = { Text("Italic", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                                    leadingIcon = { Icon(Icons.Default.FormatItalic, "Italic", modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                        // Text Alignment
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Alignment", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("left" to Icons.AutoMirrored.Filled.FormatAlignLeft, "center" to Icons.Default.FormatAlignCenter, "right" to Icons.AutoMirrored.Filled.FormatAlignRight).forEach { (key, icon) ->
                                    FilterChip(
                                        selected = state.subtitleAlignment == key,
                                        onClick = { viewModel.setSubtitleAlignment(key) },
                                        label = { Icon(icon, key, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }

                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vertical Position", style = MaterialTheme.typography.bodyMedium)
                            Text("${currentPosition}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        HapticSlider(
                            value = currentPosition.toFloat(),
                            onValueChange = { setPosition(it.roundToInt()) },
                            valueRange = 0f..100f,
                            steps = 100,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            hapticsEnabled = state.hapticFeedbackEnabled
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Background Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(currentBgOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        HapticSlider(
                            value = currentBgOpacity,
                            onValueChange = setBgOpacity,
                            valueRange = 0f..1f,
                            steps = 10,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            hapticsEnabled = state.hapticFeedbackEnabled
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var edgeTypeExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Edge Type",
                        subtitle = when(currentEdgeType) {
                            "none" -> "None"
                            "outline" -> "Outline"
                            "dropshadow" -> "Drop Shadow"
                            "raised" -> "Raised"
                            "depressed" -> "Depressed"
                            else -> currentEdgeType
                        },
                        expanded = edgeTypeExpanded,
                        onToggle = { edgeTypeExpanded = !edgeTypeExpanded },
                        icon = Icons.Default.Title
                    ) {
                        listOf("none" to "None", "outline" to "Outline", "dropshadow" to "Drop Shadow", "raised" to "Raised", "depressed" to "Depressed").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { setEdgeType(k); edgeTypeExpanded = false })
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Text Color", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFFFFFFFF, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .border(2.dp, if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                        .clickable { setColor(color) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
