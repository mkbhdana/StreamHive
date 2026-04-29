package com.mkbhdana.streamhive.catalog.info

import androidx.lifecycle.SavedStateHandle
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
    val error: String? = null,
    val metadata: TmdbMetadataEntity? = null,

    // All video files associated with this item (direct + nested)
    val driveFiles: List<MediaFileEntity> = emptyList(),

    // For TV shows: files grouped by season extracted from filename
    val fileSeasons: List<FileSeason> = emptyList(),
    val expandedSeason: Int? = null,

    // Preferred engine from settings
    val preferredEngine: PlayerEngine = PlayerEngine.EXO_PLAYER
)

@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val driveRepository: DriveRepository,
    private val mediaFileDao: MediaFileDao,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val driveFileId: String = savedStateHandle.get<String>("driveFileId") ?: ""

    /** Catalog type hint from navigation: "movie", "tv", or "auto" */
    private val mediaTypeHint: String = savedStateHandle.get<String>("mediaType") ?: "auto"

    private val _uiState = MutableStateFlow(
        MediaInfoUiState(
            preferredEngine = appPreferences.preferredEngine
        )
    )
    val uiState: StateFlow<MediaInfoUiState> = _uiState.asStateFlow()

    init {
        loadMediaInfo()
    }

    private fun loadMediaInfo() {
        viewModelScope.launch {
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
                //    This gives us video files, season sub-folders, and any .txt hint files
                val folderChildren = if (driveFile != null && driveFile.isFolder) {
                    val result = driveRepository.listFilesInDrive(
                        driveId = driveFile.driveId,
                        folderId = driveFile.id
                    )
                    result.getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }

                // 3. Check for .txt file with TMDB/IMDB ID as filename
                //    Uses a separate API call since the main query only returns video files
                //    txt file is at: series folder → [S01/, S02/, tt1234567.txt]
                //    or: movie folder → [movie.mkv, 12345.txt]
                val metadataIdFromFile = if (driveFile != null && driveFile.isFolder) {
                    val txtFiles = driveRepository.listTextFilesInFolder(
                        driveId = driveFile.driveId,
                        folderId = driveFile.id
                    )
                    detectMetadataIdFile(txtFiles)
                } else null

                // 4. Get metadata — priority: cache → text file ID → name search
                var metadata = tmdbRepository.getMetadataForFile(driveFileId)

                if (metadata == null && metadataIdFromFile != null) {
                    // Auto-fix using text file hint
                    metadata = tmdbRepository.fixMetadataById(
                        driveFileId, metadataIdFromFile, mediaTypeHint
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

                // If cached metadata exists but text file provides a different ID,
                // re-fetch to update (in case user added/changed the text file)
                if (metadata != null && metadataIdFromFile != null) {
                    val currentIdStr = metadata.tmdbId.toString()
                    val isImdb = metadataIdFromFile.startsWith("tt", ignoreCase = true)
                    if (isImdb || currentIdStr != metadataIdFromFile) {
                        val updated = tmdbRepository.fixMetadataById(
                            driveFileId, metadataIdFromFile, mediaTypeHint
                        )
                        if (updated != null) metadata = updated
                    }
                }

                // 5. Collect video files (skip .txt and other non-video files)
                val allFiles = collectVideoFiles(driveFile, folderChildren)

                // 6. Determine if we should show season grouping:
                //    - TMDB confirms it's TV → always group
                //    - No TMDB data but catalog hint is "tv" → group by season
                //    - Otherwise → flat file list
                val isTv = metadata?.mediaType == "tv" || (metadata == null && mediaTypeHint == "tv")
                val seasons = if (isTv) {
                    groupFilesBySeason(allFiles)
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metadata = metadata,
                        driveFiles = allFiles,
                        fileSeasons = seasons,
                        expandedSeason = seasons.firstOrNull()?.seasonNumber
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load info: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Detect a .txt file in the folder whose filename is a TMDB or IMDB ID.
     * Supported patterns:
     *   - "12345.txt" → TMDB ID
     *   - "tt1234567.txt" → IMDB ID
     *   - "tmdb-12345.txt" → TMDB ID
     *   - "imdb-tt1234567.txt" → IMDB ID
     */
    private fun detectMetadataIdFile(children: List<DriveFile>): String? {
        val txtFiles = children.filter {
            !it.isFolder && it.name.endsWith(".txt", ignoreCase = true)
        }

        for (txt in txtFiles) {
            val nameWithoutExt = txt.name.substringBeforeLast(".")

            // Direct IMDB ID: tt1234567
            if (nameWithoutExt.matches(Regex("""tt\d{5,}""", RegexOption.IGNORE_CASE))) {
                return nameWithoutExt
            }

            // Direct TMDB numeric ID: 12345
            if (nameWithoutExt.matches(Regex("""\d+"""))) {
                return nameWithoutExt
            }

            // Prefixed: tmdb-12345 or imdb-tt1234567
            val prefixed = Regex("""(?:tmdb|imdb)[- _](.+)""", RegexOption.IGNORE_CASE)
                .find(nameWithoutExt)
            if (prefixed != null) {
                return prefixed.groupValues[1].trim()
            }
        }

        return null
    }

    /**
     * Collect video files from folder children.
     * Recurses into sub-folders (season folders).
     * Filters out non-video files (like .txt metadata hints).
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
            // Non-video files (.txt, .nfo, etc.) are silently skipped
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
            _uiState.update { it.copy(isLoading = true) }
            val result = tmdbRepository.fixMetadataById(driveFileId, idInput, mediaTypeHint)
            if (result != null) {
                val isTv = result.mediaType == "tv" || mediaTypeHint == "tv"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metadata = result,
                        error = null,
                        fileSeasons = if (isTv) groupFilesBySeason(it.driveFiles) else emptyList()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not find metadata for that ID")
                }
            }
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "ts", "mts", "m2ts", "mpg", "mpeg", "3gp", "ogv", "vob"
        )
    }
}
