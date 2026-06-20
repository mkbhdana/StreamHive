package com.mkbhdana.streamhive.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.settings.SettingsViewModel
import com.mkbhdana.streamhive.settings.SourcePriorityOption
import com.mkbhdana.streamhive.settings.SourcePriorityOptions
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor as DarkSurface
import com.mkbhdana.streamhive.tv.theme.TvTextPrimaryColor as TextPrimary
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor as TextSecondary
import com.mkbhdana.streamhive.util.FileUtils

private val DECODER_OPTIONS = listOf("hw" to "HW", "hw+" to "HW+", "auto" to "Auto", "sw" to "SW")
private val RESIZE_OPTIONS = listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom", "16:9" to "16:9", "4:3" to "4:3")
private val AUDIO_LANGS = listOf(
    "original" to "Original", "en" to "English", "ja" to "Japanese", "ko" to "Korean",
    "hi" to "Hindi", "es" to "Spanish", "fr" to "French", "de" to "German"
)
private val SUB_LANGS = listOf(
    "none" to "Off", "en" to "English", "ja" to "Japanese", "ko" to "Korean",
    "hi" to "Hindi", "es" to "Spanish", "fr" to "French", "de" to "German"
)
private val EXCLUDE_LANGS = listOf(
    "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
    "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
    "ru" to "Russian", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil",
    "te" to "Telugu", "ml" to "Malayalam", "gu" to "Gujarati", "ar" to "Arabic",
    "pt" to "Portuguese", "tr" to "Turkish", "th" to "Thai", "vi" to "Vietnamese", "id" to "Indonesian"
)
private val SUB_COLORS = listOf(
    0xFFFFFFFFL to "White", 0xFFFFFF00L to "Yellow", 0xFF00E5FFL to "Cyan",
    0xFF69F0AEL to "Green", 0xFFFF5252L to "Red", 0xFF000000L to "Black"
)
private val SUB_ALIGN = listOf("left" to "Left", "center" to "Center", "right" to "Right")

@Composable
fun TvPlayerPane(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val s = viewModel.uiState
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TvChoiceSetting(
            "Preferred Player",
            listOf("EXO_PLAYER" to "ExoPlayer", "MPV" to "MPV", "EXTERNAL" to "External"),
            s.preferredEngine.name
        ) { viewModel.setPreferredEngine(PlayerEngine.valueOf(it)) }
        TvChoiceSetting("ExoPlayer Decoder", DECODER_OPTIONS, s.exoDecoder, viewModel::setExoDecoder)
        TvChoiceSetting("MPV Decoder", DECODER_OPTIONS, s.mpvDecoder, viewModel::setMpvDecoder)
        TvChoiceSetting("Default Resize Mode", RESIZE_OPTIONS, s.defaultResizeMode, viewModel::setDefaultResizeMode)
        TvToggleSetting("Tunneled Playback", s.tunneledPlaybackEnabled, viewModel::setTunneledPlaybackEnabled)
        TvToggleSetting("Map Dolby Vision 7 → HEVC", s.mapDv7ToHevc, viewModel::setMapDv7ToHevc)

        Spacer(Modifier.height(12.dp))
        Text("Source Priority", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(
            "When a title has multiple files, prefer these in order.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        SourcePriorityCategory("Resolution", SourcePriorityOptions.resolutions, s.sourcePriorityResolutions, viewModel::setSourcePriorityResolutions)
        SourcePriorityCategory("Video Format", SourcePriorityOptions.videoFormats, s.sourcePriorityVideoFormats, viewModel::setSourcePriorityVideoFormats)
        SourcePriorityCategory("Codec", SourcePriorityOptions.decoders, s.sourcePriorityDecoders, viewModel::setSourcePriorityDecoders)
        SourcePriorityCategory("Container", SourcePriorityOptions.containers, s.sourcePriorityContainers, viewModel::setSourcePriorityContainers)
    }
}

@Composable
private fun SourcePriorityCategory(
    label: String,
    options: List<SourcePriorityOption>,
    savedOrder: List<String>,
    onReorder: (List<String>) -> Unit
) {
    val ids = options.map { it.id }
    val ordered = if (savedOrder.isEmpty()) ids
    else savedOrder.filter { it in ids } + ids.filter { it !in savedOrder }
    val items = ordered.map { id -> id to (options.firstOrNull { it.id == id }?.label ?: id) }
    TvReorderSetting(
        label = label,
        items = items,
        onMoveUp = { idx ->
            if (idx > 0) {
                val m = ordered.toMutableList(); val t = m.removeAt(idx); m.add(idx - 1, t); onReorder(m)
            }
        },
        onMoveDown = { idx ->
            if (idx < ordered.lastIndex) {
                val m = ordered.toMutableList(); val t = m.removeAt(idx); m.add(idx + 1, t); onReorder(m)
            }
        }
    )
}

@Composable
fun TvSubtitlesPane(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val s = viewModel.uiState
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TvChoiceSetting("Preferred Audio Language", AUDIO_LANGS, s.preferredAudioLanguage, viewModel::setPreferredAudioLanguage)
        TvChoiceSetting("Preferred Subtitle Language", SUB_LANGS, s.preferredSubtitleLanguage, viewModel::setPreferredSubtitleLanguage)
        TvMultiChoiceSetting("Excluded Languages", EXCLUDE_LANGS, s.subtitleExcludeLanguages) { code ->
            val current = s.subtitleExcludeLanguages
            viewModel.setSubtitleExcludeLanguages(if (code in current) current - code else current + code)
        }
        TvToggleSetting("LibASS subtitles (MPV)", s.libassSubtitlesEnabled, viewModel::setLibassSubtitlesEnabled)

        Spacer(Modifier.height(12.dp))
        Text("ExoPlayer Subtitle Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        TvStepperSetting("Size", "${s.exoSubtitleFontSize}",
            { viewModel.setExoSubtitleFontSize((s.exoSubtitleFontSize - 1).coerceIn(10, 48)) },
            { viewModel.setExoSubtitleFontSize((s.exoSubtitleFontSize + 1).coerceIn(10, 48)) })
        TvChoiceSetting("Color", SUB_COLORS.map { it.first.toString() to it.second }, s.exoSubtitleColor.toString()) {
            viewModel.setExoSubtitleColor(it.toLong())
        }
        TvStepperSetting("Position", "${s.exoSubtitlePosition}",
            { viewModel.setExoSubtitlePosition((s.exoSubtitlePosition - 5).coerceIn(0, 100)) },
            { viewModel.setExoSubtitlePosition((s.exoSubtitlePosition + 5).coerceIn(0, 100)) })
        TvStepperSetting("Background", "${(s.exoSubtitleBgOpacity * 100).toInt()}%",
            { viewModel.setExoSubtitleBgOpacity((s.exoSubtitleBgOpacity - 0.1f).coerceIn(0f, 1f)) },
            { viewModel.setExoSubtitleBgOpacity((s.exoSubtitleBgOpacity + 0.1f).coerceIn(0f, 1f)) })
        TvStepperSetting("Outline", "${s.exoSubtitleEdgeSize}",
            { viewModel.setExoSubtitleEdgeSize((s.exoSubtitleEdgeSize - 1).coerceIn(0, 20)) },
            { viewModel.setExoSubtitleEdgeSize((s.exoSubtitleEdgeSize + 1).coerceIn(0, 20)) })

        Spacer(Modifier.height(12.dp))
        Text("MPV Subtitle Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        TvStepperSetting("Size", "${s.mpvSubtitleFontSize}",
            { viewModel.setMpvSubtitleFontSize((s.mpvSubtitleFontSize - 1).coerceIn(10, 48)) },
            { viewModel.setMpvSubtitleFontSize((s.mpvSubtitleFontSize + 1).coerceIn(10, 48)) })
        TvChoiceSetting("Color", SUB_COLORS.map { it.first.toString() to it.second }, s.mpvSubtitleColor.toString()) {
            viewModel.setMpvSubtitleColor(it.toLong())
        }
        TvStepperSetting("Position", "${s.mpvSubtitlePosition}",
            { viewModel.setMpvSubtitlePosition((s.mpvSubtitlePosition - 5).coerceIn(0, 100)) },
            { viewModel.setMpvSubtitlePosition((s.mpvSubtitlePosition + 5).coerceIn(0, 100)) })
        TvStepperSetting("Background", "${(s.mpvSubtitleBgOpacity * 100).toInt()}%",
            { viewModel.setMpvSubtitleBgOpacity((s.mpvSubtitleBgOpacity - 0.1f).coerceIn(0f, 1f)) },
            { viewModel.setMpvSubtitleBgOpacity((s.mpvSubtitleBgOpacity + 0.1f).coerceIn(0f, 1f)) })
        TvStepperSetting("Outline", "${s.mpvSubtitleEdgeSize}",
            { viewModel.setMpvSubtitleEdgeSize((s.mpvSubtitleEdgeSize - 1).coerceIn(0, 20)) },
            { viewModel.setMpvSubtitleEdgeSize((s.mpvSubtitleEdgeSize + 1).coerceIn(0, 20)) })

        Spacer(Modifier.height(12.dp))
        Text("Shared", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        TvStepperSetting("Scale", "%.1f".format(s.subtitleScale),
            { viewModel.setSubtitleScale((s.subtitleScale - 0.1f).coerceIn(0.5f, 3.0f)) },
            { viewModel.setSubtitleScale((s.subtitleScale + 0.1f).coerceIn(0.5f, 3.0f)) })
        TvChoiceSetting("Alignment", SUB_ALIGN, s.subtitleAlignment, viewModel::setSubtitleAlignment)
        TvToggleSetting("Bold", s.subtitleBold, viewModel::setSubtitleBold)
        TvToggleSetting("Italic", s.subtitleItalic, viewModel::setSubtitleItalic)
    }
}

@Composable
fun TvTmdbPane(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val s = viewModel.uiState
    val folders by viewModel.availableFolders.collectAsState()
    var showBrowser by remember { mutableStateOf(false) }
    val nameOf = { id: String -> folders.firstOrNull { it.id == id }?.name ?: id }
    val typeOf = { id: String -> if (id in s.tmdbMovieFolders) "Movies" else "Series" }

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TvTextFieldSetting("TMDB API Key", s.tmdbApiKey, viewModel::setTmdbApiKey)

        Spacer(Modifier.height(8.dp))
        Text("Catalog Folders", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        val ordered = viewModel.getOrderedFolderIds()
        if (ordered.isEmpty()) {
            Text("No folders mapped yet.", color = TextSecondary)
        } else {
            TvReorderSetting(
                label = "Order",
                items = ordered.map { id -> id to "${nameOf(id)}  ·  ${typeOf(id)}" },
                onMoveUp = { idx -> viewModel.moveFolderUp(ordered[idx]) },
                onMoveDown = { idx -> viewModel.moveFolderDown(ordered[idx]) }
            )
            Spacer(Modifier.height(8.dp))
            ordered.forEach { id ->
                TvActionSetting(nameOf(id), "${typeOf(id)} — select to remove") { viewModel.removeTmdbFolder(id) }
            }
        }

        Spacer(Modifier.height(12.dp))
        TvActionSetting("Add catalog folder", "Browse your drives") {
            viewModel.initFolderBrowser()
            showBrowser = true
        }
    }

    if (showBrowser) {
        TvFolderBrowserDialog(viewModel = viewModel, onDismiss = { showBrowser = false })
    }
}

@Composable
private fun TvFolderBrowserDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val browser = viewModel.folderBrowserState

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = DarkSurface) {
            Column(modifier = Modifier.width(520.dp).padding(20.dp)) {
                Text(
                    text = browser.folderStack.lastOrNull()?.second
                        ?: browser.drives.firstOrNull { it.id == browser.selectedDriveId }?.name
                        ?: "Select a drive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (browser.selectedDriveId == null) {
                        items(browser.drives, key = { it.id }) { drive ->
                            TvActionSetting(drive.name) { viewModel.browserSelectDrive(drive.id) }
                        }
                    } else {
                        item { TvActionSetting("‹ Back") { viewModel.browserGoBack() } }
                        items(browser.currentFolders, key = { it.id }) { folder ->
                            TvActionSetting(folder.name) { viewModel.browserOpenFolder(folder.id, folder.name) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                val current = viewModel.browserCurrentFolderId()
                if (browser.selectedDriveId != null && current != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvChip("Add as Movies", selected = false) {
                            viewModel.addTmdbFolder(current, "movie"); onDismiss()
                        }
                        TvChip("Add as Series", selected = false) {
                            viewModel.addTmdbFolder(current, "tv"); onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvStoragePane(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    var sizes by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(Unit) {
        viewModel.calculateCacheSizes { image, db -> sizes = image to db }
    }
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        sizes?.let { (image, db) ->
            Text(
                "Image cache: ${FileUtils.formatFileSize(image)}   •   Catalog DB: ${FileUtils.formatFileSize(db)}",
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        TvActionSetting("Clear Cache & Catalog Data", "Removes cached images and TMDB catalog") {
            viewModel.clearCacheAndData { sizes = 0L to 0L }
        }
        TvActionSetting("Clear Watch History", "Removes Continue Watching items") {
            viewModel.clearPlaybackCacheAndData { }
        }
        TvActionSetting("Reset Preferences", "Restore default settings (keeps catalog)") {
            viewModel.resetPreferences { }
        }
    }
}

@Composable
fun TvAboutPane(viewModel: SettingsViewModel, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "Unknown"
    }
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("StreamHive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("v$version", color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))
        TvActionSetting(
            "Check for Updates",
            if (viewModel.uiState.isCheckingForUpdate) "Checking…" else "Last checked: ${formatLastChecked(viewModel.uiState.lastUpdateCheckAt)}"
        ) { viewModel.checkForUpdates() }
        Spacer(Modifier.height(12.dp))
        TvActionSetting("Log Out", "Sign out of StreamHive on this TV") { onLogout() }
    }
}

private fun formatLastChecked(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}
