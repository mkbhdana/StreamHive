package com.mkbhdana.streamhive.catalog.info

import com.mkbhdana.streamhive.navigation.MediaInfoRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.data.model.DriveFile
import com.mkbhdana.streamhive.data.tmdb.TmdbRepository
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.settings.AppPreferences
import com.mkbhdana.streamhive.settings.SourcePriorityFilter
import com.mkbhdana.streamhive.settings.SourcePriorityResult
import com.mkbhdana.streamhive.util.EpisodeMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents a season extracted from file names (e.g. S01, S02).
 */
data class FileSeason(
    val seasonNumber: Int,
    val label: String,
    val files: List<MediaFileEntity>
)

data class MediaInfoUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val metadata: TmdbMetadataEntity? = null,

    // All video files associated with this item (direct + nested)
    val allDriveFiles: List<MediaFileEntity> = emptyList(),
    val driveFiles: List<MediaFileEntity> = emptyList(),

    // For TV shows: files grouped by season extracted from filename
    val fileSeasons: List<FileSeason> = emptyList(),
    val expandedSeason: Int? = null,

    val sourcePriorityConfigured: Boolean = false,
    val sourcePriorityTemporarilyDisabled: Boolean = false,
    val sourcePriorityFiltered: Boolean = false,
    val sourcePrioritySummary: String? = null,

    // Requested media type from navigation
    val requestedMediaType: String = "auto"
)

@HiltViewModel(assistedFactory = MediaInfoViewModel.Factory::class)
class MediaInfoViewModel @AssistedInject constructor(
    private val tmdbRepository: TmdbRepository,
    private val driveRepository: DriveRepository,
    private val mediaFileDao: MediaFileDao,
    private val appPreferences: AppPreferences,
    @Assisted private val navKey: MediaInfoRoute
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(navKey: MediaInfoRoute): MediaInfoViewModel
    }

    private val driveFileId: String = navKey.driveFileId

    /** Catalog type hint from navigation: "movie", "tv", or "auto" */
    private val mediaTypeHint: String = navKey.mediaType

    private var sourcePriorityFilteringEnabled = false
    private var metadataRevision = 0

    private val _uiState = MutableStateFlow(
        MediaInfoUiState(
            requestedMediaType = mediaTypeHint
        )
    )
    val uiState: StateFlow<MediaInfoUiState> = _uiState.asStateFlow()

    fun getPreferredEngine(): com.mkbhdana.streamhive.player.mpv.PlayerEngine = appPreferences.preferredEngine

    init {
        loadMediaInfo()
    }

    /**
     * Force a fresh reload of files + metadata. The TV detail screen reuses a
     * keyed ViewModel across navigations, so without this it would keep showing the
     * snapshot captured the first time the item was opened.
     */
    fun refresh() {
        loadMediaInfo()
    }

    private fun loadMediaInfo() {
        viewModelScope.launch {
            val loadRevision = metadataRevision
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1. Get the drive file entity (movie-name or series-name folder)
                var driveFile = mediaFileDao.getFileById(driveFileId)
                if (driveFile == null) {
                    val apiFile = driveRepository.getFileByIdViaApi(driveFileId).getOrNull()
                    if (apiFile != null) {
                        driveFile = driveFileToEntity(apiFile, apiFile.parents?.firstOrNull() ?: "", apiFile.driveId ?: "")
                    }
                }

                // 2. Fetch folder children from Drive API
                //    This gives us video files and season sub-folders.
                val folderChildren = if (driveFile != null && driveFile.isFolder) {
                    val result = driveRepository.listFilesInDrive(
                        driveId = driveFile.driveId,
                        folderId = driveFile.id
                    )
                    result.getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }

                val metadataIdFromFolderName = driveFile?.name?.let(::detectMetadataIdInName)

                // 3. Get metadata: cache, folder-name ID, then name search.
                var metadata = tmdbRepository.getMetadataForFile(driveFileId)
                if (metadata != null && metadata.needsDisplayRepair) {
                    metadata = tmdbRepository.repairMetadataIfIncomplete(metadata, mediaTypeHint)
                }

                if (metadata == null && metadataIdFromFolderName != null) {
                    // Auto-fix using folder-name ID hint
                    metadata = tmdbRepository.fixMetadataById(
                        driveFileId, metadataIdFromFolderName, mediaTypeHint
                    )
                }

                if (metadata == null && driveFile != null) {
                    // Fall back to folder name search
                    metadata = tmdbRepository.fetchAndCacheMetadata(
                        driveFileId = driveFileId,
                        name = driveFile.name,
                        mediaType = mediaTypeHint
                    )
                }

                // If cached metadata exists but the folder name provides a different ID,
                // re-fetch to update in case the user renamed the folder.
                if (metadata != null && metadataIdFromFolderName != null) {
                    val currentIdStr = metadata.tmdbId.toString()
                    val isImdb = metadataIdFromFolderName.startsWith("tt", ignoreCase = true)
                    if (isImdb || currentIdStr != metadataIdFromFolderName) {
                        val updated = tmdbRepository.fixMetadataById(
                            driveFileId, metadataIdFromFolderName, mediaTypeHint
                        )
                        if (updated != null) metadata = updated
                    }
                }

                // 4. Collect video files.
                val allFiles = collectVideoFiles(driveFile, folderChildren)

                // 5. Determine if we should show season grouping:
                //    - TMDB confirms it's TV → always group
                //    - No TMDB data but catalog hint is "tv" → group by season
                //    - Otherwise → flat file list
                val isTv = metadata?.mediaType == "tv" || (metadata == null && mediaTypeHint == "tv")
                val display = buildFileDisplay(allFiles, isTv, _uiState.value.expandedSeason)

                if (loadRevision == metadataRevision) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            metadata = metadata,
                            allDriveFiles = allFiles,
                            driveFiles = display.files,
                            fileSeasons = display.seasons,
                            expandedSeason = display.expandedSeason,
                            sourcePriorityConfigured = display.sourcePriorityConfigured,
                            sourcePriorityFiltered = display.sourcePriorityFiltered,
                            sourcePrioritySummary = display.sourcePrioritySummary
                        )
                    }
                }
            } catch (e: Exception) {
                if (loadRevision == metadataRevision) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load info: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Detect a TMDB or IMDB ID embedded in the folder name.
     * Supported patterns:
     *   - "Movie Name [12345]"
     *   - "Movie Name [tmdb-12345]"
     *   - "Movie Name [tmdb:12345]"
     *   - "Movie Name [tt1234567]"
     *   - "Movie Name [imdb-tt1234567]"
     *
     * Plain numbers outside square brackets are ignored so years like "(1998)"
     * do not get treated as TMDB IDs.
     */
    private fun detectMetadataIdInName(name: String): String? {
        val bracketed = Regex("""\[\s*(tt\d{5,}|\d+)\s*]""", RegexOption.IGNORE_CASE)
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (bracketed != null) return bracketed

        val explicitTmdb = Regex("""\btmdb(?:\s*[-_ ]?\s*id)?\s*[-_:# ]\s*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (explicitTmdb != null) return explicitTmdb

        val explicitImdb = Regex("""\bimdb(?:\s*[-_ ]?\s*id)?\s*[-_:# ]\s*(tt\d{5,})\b""", RegexOption.IGNORE_CASE)
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (explicitImdb != null) return explicitImdb

        return Regex("""\btt\d{5,}\b""", RegexOption.IGNORE_CASE)
            .find(name)
            ?.value
    }

    /**
     * Collect video files from folder children.
     * Recurses into sub-folders (season folders).
     * Filters out non-video files.
     */
    private suspend fun collectVideoFiles(
        driveFile: MediaFileEntity?,
        folderChildren: List<DriveFile>
    ): List<MediaFileEntity> {
        if (driveFile == null) return emptyList()

        if (!driveFile.isFolder) {
            return listOf(driveFile)
        }

        val videos = mutableListOf<MediaFileEntity>()

        for (child in folderChildren) {
            if (child.isFolder) {
                // Sub-folder (likely season folder) — fetch its contents
                val subResult = driveRepository.listFilesInDrive(
                    driveId = driveFile.driveId,
                    folderId = child.id
                )
                val subFiles = subResult.getOrNull() ?: continue
                for (subFile in subFiles) {
                    if (!subFile.isFolder && isVideoFile(subFile)) {
                        videos.add(driveFileToEntity(subFile, child.id, driveFile.driveId))
                    }
                }
            } else if (isVideoFile(child)) {
                videos.add(driveFileToEntity(child, driveFile.id, driveFile.driveId))
            }
            // Non-video files are silently skipped.
        }

        return videos.sortedBy { it.name }
    }

    /**
     * Check if a DriveFile is a video based on mimeType or extension.
     */
    private fun isVideoFile(file: DriveFile): Boolean {
        if (file.mimeType.startsWith("video/")) return true
        val ext = file.fileExtension?.lowercase()
            ?: file.name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /**
     * Convert a Drive API file to a MediaFileEntity for UI display.
     */
    private fun driveFileToEntity(file: DriveFile, parentId: String, driveId: String): MediaFileEntity {
        return MediaFileEntity(
            id = file.id,
            name = file.name,
            mimeType = file.mimeType,
            size = file.size,
            thumbnailLink = file.thumbnailLink,
            modifiedTime = file.modifiedTime,
            parentId = parentId,
            driveId = driveId,
            fileExtension = file.fileExtension,
            isFolder = file.isFolder,
            videoWidth = file.videoMediaMetadata?.width,
            videoHeight = file.videoMediaMetadata?.height,
            videoDurationMs = file.videoMediaMetadata?.durationMillis
        )
    }

    /**
     * Group video files into seasons by extracting season numbers from filenames.
     * Pattern: S01, S02, s1, Season 1, etc.
     * Also detects season from parent folder name (e.g. files from "S01/" folder).
     * Files without a recognizable season go into "Other Files".
     */
    private fun groupFilesBySeason(files: List<MediaFileEntity>): List<FileSeason> {
        val seasonRegex = Regex("""[Ss](\d{1,2})""")
        val seasonWordRegex = Regex("""[Ss]eason\s*(\d{1,2})""", RegexOption.IGNORE_CASE)

        val grouped = mutableMapOf<Int, MutableList<MediaFileEntity>>()
        val unsorted = mutableListOf<MediaFileEntity>()

        for (file in files) {
            val name = file.name
            val match = seasonRegex.find(name) ?: seasonWordRegex.find(name)
            if (match != null) {
                val seasonNum = match.groupValues[1].toIntOrNull() ?: 0
                grouped.getOrPut(seasonNum) { mutableListOf() }.add(file)
            } else {
                unsorted.add(file)
            }
        }

        val seasons = grouped.entries
            .sortedBy { it.key }
            .map { (num, fileList) ->
                FileSeason(
                    seasonNumber = num,
                    label = "Season $num",
                    files = fileList.sortedBy { it.name }
                )
            }
            .toMutableList()

        if (unsorted.isNotEmpty()) {
            seasons.add(
                FileSeason(
                    seasonNumber = -1,
                    label = "Other Files",
                    files = unsorted.sortedBy { it.name }
                )
            )
        }

        return seasons
    }

    fun toggleSeasonExpanded(seasonNumber: Int) {
        _uiState.update {
            it.copy(
                expandedSeason = if (it.expandedSeason == seasonNumber) null else seasonNumber
            )
        }
    }

    fun fixMetadata(idInput: String) {
        viewModelScope.launch {
            metadataRevision += 1
            _uiState.update { it.copy(isLoading = true) }
            val lookupType = mediaTypeHint
                .takeIf { it == "movie" || it == "tv" }
                ?: _uiState.value.metadata?.mediaType
                    ?.takeIf { it == "movie" || it == "tv" }
                ?: "auto"
            val result = tmdbRepository.fixMetadataById(driveFileId, idInput, lookupType)
            if (result != null) {
                val isTv = result.mediaType == "tv"
                val allFiles = _uiState.value.allDriveFiles.ifEmpty { _uiState.value.driveFiles }
                val display = buildFileDisplay(allFiles, isTv, _uiState.value.expandedSeason)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metadata = result,
                        error = null,
                        allDriveFiles = allFiles,
                        driveFiles = display.files,
                        fileSeasons = display.seasons,
                        expandedSeason = display.expandedSeason,
                        sourcePriorityConfigured = display.sourcePriorityConfigured,
                        sourcePriorityFiltered = display.sourcePriorityFiltered,
                        sourcePrioritySummary = display.sourcePrioritySummary
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not find metadata for that ID")
                }
            }
        }
    }

    /** Pull-to-refresh: re-fetch files from Drive API */
    fun refreshFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val driveFile = mediaFileDao.getFileById(driveFileId)
                if (driveFile != null && driveFile.isFolder) {
                    val result = driveRepository.listFilesInDrive(
                        driveId = driveFile.driveId,
                        folderId = driveFile.id
                    )
                    val folderChildren = result.getOrNull() ?: emptyList()
                    val allFiles = collectVideoFiles(driveFile, folderChildren)
                    val isTv = _uiState.value.metadata?.mediaType == "tv" || mediaTypeHint == "tv"
                    val display = buildFileDisplay(allFiles, isTv, _uiState.value.expandedSeason)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            allDriveFiles = allFiles,
                            driveFiles = display.files,
                            fileSeasons = display.seasons,
                            expandedSeason = display.expandedSeason,
                            sourcePriorityConfigured = display.sourcePriorityConfigured,
                            sourcePriorityFiltered = display.sourcePriorityFiltered,
                            sourcePrioritySummary = display.sourcePrioritySummary
                        )
                    }
                } else {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = "Refresh failed: ${e.message}") }
            }
        }
    }

    fun setSourcePriorityFilteringEnabled(enabled: Boolean) {
        if (sourcePriorityFilteringEnabled == enabled) return
        sourcePriorityFilteringEnabled = enabled
        reapplySourcePriority()
    }

    fun setSourcePriorityTemporarilyDisabled(disabled: Boolean) {
        _uiState.update { it.copy(sourcePriorityTemporarilyDisabled = disabled) }
        reapplySourcePriority()
    }

    private fun reapplySourcePriority() {
        val state = _uiState.value
        val allFiles = state.allDriveFiles.ifEmpty { state.driveFiles }
        if (allFiles.isEmpty()) {
            _uiState.update {
                it.copy(
                    sourcePriorityConfigured = sourcePriorityFilteringEnabled && appPreferences.sourcePriorityConfig.hasAnyPriority,
                    sourcePriorityFiltered = false,
                    sourcePrioritySummary = null
                )
            }
            return
        }

        val isTv = state.metadata?.mediaType == "tv" || (state.metadata == null && mediaTypeHint == "tv")
        val display = buildFileDisplay(allFiles, isTv, state.expandedSeason)
        _uiState.update {
            it.copy(
                allDriveFiles = allFiles,
                driveFiles = display.files,
                fileSeasons = display.seasons,
                expandedSeason = display.expandedSeason,
                sourcePriorityConfigured = display.sourcePriorityConfigured,
                sourcePriorityFiltered = display.sourcePriorityFiltered,
                sourcePrioritySummary = display.sourcePrioritySummary
            )
        }
    }

    private data class FileDisplayState(
        val files: List<MediaFileEntity>,
        val seasons: List<FileSeason>,
        val expandedSeason: Int?,
        val sourcePriorityConfigured: Boolean,
        val sourcePriorityFiltered: Boolean,
        val sourcePrioritySummary: String?
    )

    private fun buildFileDisplay(
        allFiles: List<MediaFileEntity>,
        isTv: Boolean,
        preferredExpandedSeason: Int?
    ): FileDisplayState {
        val config = appPreferences.sourcePriorityConfig
        val isConfigured = sourcePriorityFilteringEnabled && config.hasAnyPriority
        val priorityResult = if (isConfigured && !_uiState.value.sourcePriorityTemporarilyDisabled) {
            // For series, group by episode so a single-source episode is never dropped;
            // for movies the whole folder is one set of alternatives.
            SourcePriorityFilter.filter(
                allFiles,
                config,
                groupBy = if (isTv) EpisodeMatcher::sourceGroupKey else null
            )
        } else {
            SourcePriorityResult(
                files = allFiles,
                totalFiles = allFiles.size,
                isConfigured = isConfigured
            )
        }
        val displayFiles = priorityResult.files
        val seasons = if (isTv) groupFilesBySeason(displayFiles) else emptyList()
        val expandedSeason = when {
            seasons.isEmpty() -> null
            preferredExpandedSeason != null && seasons.any { it.seasonNumber == preferredExpandedSeason } -> preferredExpandedSeason
            else -> seasons.firstOrNull()?.seasonNumber
        }

        return FileDisplayState(
            files = displayFiles,
            seasons = seasons,
            expandedSeason = expandedSeason,
            sourcePriorityConfigured = isConfigured,
            sourcePriorityFiltered = isConfigured &&
                !_uiState.value.sourcePriorityTemporarilyDisabled &&
                priorityResult.isFiltered,
            sourcePrioritySummary = sourcePrioritySummary(priorityResult)
        )
    }

    private fun sourcePrioritySummary(result: SourcePriorityResult): String? {
        if (!sourcePriorityFilteringEnabled || !appPreferences.sourcePriorityConfig.hasAnyPriority) return null
        if (_uiState.value.sourcePriorityTemporarilyDisabled) {
            return "Source priority off. Showing all ${result.totalFiles} files."
        }

        val applied = result.applied.joinToString(" · ") {
            "${it.categoryLabel}: ${it.valueLabel}"
        }
        return if (result.isFiltered) {
            "Showing ${result.files.size} of ${result.totalFiles}${applied.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
        } else {
            "Priority on. Showing all ${result.totalFiles} files."
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "ts", "mts", "m2ts", "mpg", "mpeg", "3gp", "ogv", "vob"
        )
    }
}
