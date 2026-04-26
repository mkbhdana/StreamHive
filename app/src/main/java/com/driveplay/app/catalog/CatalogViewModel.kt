package com.driveplay.app.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveplay.app.auth.AuthRepository
import com.driveplay.app.data.db.MediaFileEntity
import com.driveplay.app.data.db.PlaybackHistoryDao
import com.driveplay.app.data.db.PlaybackHistoryEntity
import com.driveplay.app.data.db.TmdbMetadataEntity
import com.driveplay.app.data.model.SharedDrive
import com.driveplay.app.data.tmdb.TmdbRepository
import com.driveplay.app.player.mpv.PlayerEngine
import com.driveplay.app.player.proxy.StreamProxyServer
import com.driveplay.app.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { CURRENT_DRIVE, ALL_DRIVES }

// Unified section model for all Drive sources
sealed class DriveSection(val label: String, val icon: ImageVector, val id: String) {
    object MyDrive : DriveSection("My Drive", Icons.Default.Home, "my_drive")
    object SharedWithMe : DriveSection("Shared with Me", Icons.Default.People, "shared_with_me")
    object Starred : DriveSection("Starred", Icons.Default.Star, "starred")
    object Recent : DriveSection("Recent", Icons.Default.Schedule, "recent")
    object Trash : DriveSection("Trash", Icons.Default.Delete, "trash")
    data class SharedDriveSection(val drive: SharedDrive) : DriveSection(drive.name, Icons.Default.CloudQueue, drive.id)
}

data class CatalogUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false, // subtle refresh vs full loading
    val sharedDrives: List<SharedDrive> = emptyList(),
    val selectedDrive: SharedDrive? = null,
    val files: List<MediaFileEntity> = emptyList(),
    val folderStack: List<FolderInfo> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchMode: SearchMode = SearchMode.CURRENT_DRIVE,
    val searchResults: Map<String, List<MediaFileEntity>> = emptyMap(), // driveId -> files for grouped search
    val error: String? = null,
    val selectedEngine: PlayerEngine = PlayerEngine.EXO_PLAYER,
    val isMpvAvailable: Boolean = false,
    val isNavigating: Boolean = false,

    // Drive sections
    val driveSections: List<DriveSection> = emptyList(),
    val selectedSection: DriveSection? = null,

    // TMDB
    val tmdbMetadata: Map<String, TmdbMetadataEntity> = emptyMap(),

    // Home tab content
    val homeMovies: List<MediaFileEntity> = emptyList(),
    val homeTvShows: List<MediaFileEntity> = emptyList(),
    val homeAnime: List<MediaFileEntity> = emptyList(),
    val isHomeLoading: Boolean = false,
    val hasTmdbSetup: Boolean = false,

    // Continue playing
    val continuePlayingItems: List<PlaybackHistoryEntity> = emptyList()
)

data class FolderInfo(
    val id: String,
    val name: String
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val authRepository: AuthRepository,
    private val appPreferences: AppPreferences,
    private val tmdbRepository: TmdbRepository,
    private val playbackHistoryDao: PlaybackHistoryDao,
    @Suppress("unused") private val streamProxyServer: StreamProxyServer // ensures proxy starts early
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var loadFilesJob: Job? = null
    private var cacheCollectionJob: Job? = null

    // TTL cache: folderKey -> timestamp of last API fetch
    private val lastLoadedTimestamps = mutableMapOf<String, Long>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    init {
        _uiState.update {
            it.copy(
                selectedEngine = appPreferences.preferredEngine,
                isMpvAvailable = appPreferences.isMpvAvailable(),
                hasTmdbSetup = appPreferences.tmdbApiKey.isNotEmpty()
            )
        }
        loadSharedDrives()
        loadContinuePlaying()
    }

    fun toggleEngine() {
        val newEngine = when (_uiState.value.selectedEngine) {
            PlayerEngine.EXO_PLAYER -> PlayerEngine.MPV
            PlayerEngine.MPV -> PlayerEngine.EXTERNAL
            PlayerEngine.EXTERNAL -> PlayerEngine.EXO_PLAYER
        }
        appPreferences.preferredEngine = newEngine
        _uiState.update { it.copy(selectedEngine = newEngine) }
    }

    fun loadSharedDrives() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            driveRepository.listSharedDrives().fold(
                onSuccess = { drives ->
                    // Build unified sections list
                    val builtInSections = listOf(
                        DriveSection.MyDrive,
                        DriveSection.SharedWithMe,
                        DriveSection.Starred,
                        DriveSection.Recent,
                        DriveSection.Trash
                    )
                    val sharedDriveSections = drives.map { DriveSection.SharedDriveSection(it) }
                    val allSections = builtInSections + sharedDriveSections

                    // Restore saved drive
                    val savedDriveId = appPreferences.selectedDriveId
                    val restoredDrive = if (savedDriveId.isNotEmpty()) {
                        drives.find { it.id == savedDriveId }
                    } else null
                    val selectedDrive = restoredDrive ?: drives.firstOrNull()

                    // Restore saved section
                    val restoredSection = if (savedDriveId.isNotEmpty()) {
                        allSections.find { it.id == savedDriveId }
                    } else null

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sharedDrives = drives,
                            selectedDrive = selectedDrive,
                            driveSections = allSections,
                            selectedSection = restoredSection ?: allSections.firstOrNull { it is DriveSection.SharedDriveSection }
                        )
                    }
                    selectedDrive?.let { selectDrive(it, isInitial = true) }
                    // Auto-load home content now that drives are available
                    loadHomeContent()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun selectDrive(drive: SharedDrive, isInitial: Boolean = false) {
        // Persist selection
        appPreferences.selectedDriveId = drive.id

        _uiState.update {
            it.copy(
                selectedDrive = drive,
                selectedSection = DriveSection.SharedDriveSection(drive),
                folderStack = emptyList(),
                files = emptyList()
            )
        }
        loadFiles(drive.id, null)
    }

    fun selectSection(section: DriveSection) {
        appPreferences.selectedDriveId = section.id
        _uiState.update {
            it.copy(
                selectedSection = section,
                selectedDrive = (section as? DriveSection.SharedDriveSection)?.drive,
                folderStack = emptyList(),
                files = emptyList(),
                isLoading = true
            )
        }

        when (section) {
            is DriveSection.SharedDriveSection -> loadFiles(section.drive.id, null)
            is DriveSection.MyDrive -> loadSectionFiles { driveRepository.listMyDriveFiles() }
            is DriveSection.SharedWithMe -> loadSectionFiles { driveRepository.listSharedWithMe() }
            is DriveSection.Starred -> loadSectionFiles { driveRepository.listStarredFiles() }
            is DriveSection.Recent -> loadSectionFiles { driveRepository.listRecentFiles() }
            is DriveSection.Trash -> loadSectionFiles { driveRepository.listTrashedFiles() }
        }
    }

    private fun loadSectionFiles(fetch: suspend () -> Result<List<com.driveplay.app.data.model.DriveFile>>) {
        loadFilesJob?.cancel()
        loadFilesJob = viewModelScope.launch {
            fetch().fold(
                onSuccess = { driveFiles ->
                    val entities = driveFiles.map { file ->
                        MediaFileEntity(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            size = file.size,
                            thumbnailLink = file.thumbnailLink,
                            modifiedTime = file.modifiedTime,
                            parentId = "section",
                            driveId = "section",
                            fileExtension = file.fileExtension,
                            isFolder = file.isFolder,
                            videoWidth = file.videoMediaMetadata?.width,
                            videoHeight = file.videoMediaMetadata?.height,
                            videoDurationMs = file.videoMediaMetadata?.durationMillis
                        )
                    }
                    _uiState.update { it.copy(isLoading = false, files = entities) }
                    fetchTmdbForFiles(entities)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    fun openFolder(folderId: String, folderName: String) {
        if (_uiState.value.isNavigating) return
        val currentDrive = _uiState.value.selectedDrive ?: return
        val currentStack = _uiState.value.folderStack
        if (currentStack.isNotEmpty() && currentStack.last().id == folderId) return

        _uiState.update {
            it.copy(
                folderStack = it.folderStack + FolderInfo(folderId, folderName),
                isNavigating = true
            )
        }
        loadFiles(currentDrive.id, folderId)
    }

    fun navigateBack(): Boolean {
        if (_uiState.value.isNavigating) return false
        val currentStack = _uiState.value.folderStack
        if (currentStack.isEmpty()) return false

        val currentDrive = _uiState.value.selectedDrive ?: return false
        val newStack = currentStack.dropLast(1)
        val parentFolderId = newStack.lastOrNull()?.id

        _uiState.update { it.copy(folderStack = newStack, isNavigating = true) }
        loadFiles(currentDrive.id, parentFolderId)
        return true
    }

    fun navigateToFolderIndex(index: Int) {
        if (_uiState.value.isNavigating) return
        val currentDrive = _uiState.value.selectedDrive ?: return
        val currentStack = _uiState.value.folderStack
        if (index < 0 || index >= currentStack.size) return
        if (index == currentStack.lastIndex) return

        val newStack = currentStack.take(index + 1)
        _uiState.update { it.copy(folderStack = newStack, isNavigating = true) }
        loadFiles(currentDrive.id, newStack.last().id)
    }

    fun navigateToRoot() {
        if (_uiState.value.isNavigating) return
        val currentDrive = _uiState.value.selectedDrive ?: return
        if (_uiState.value.folderStack.isEmpty()) return

        _uiState.update { it.copy(folderStack = emptyList(), isNavigating = true) }
        loadFiles(currentDrive.id, null)
    }

    private fun loadFiles(driveId: String, folderId: String?) {
        loadFilesJob?.cancel()
        cacheCollectionJob?.cancel()

        val cacheKey = "$driveId/${folderId ?: "root"}"

        // 1) Show cached data immediately (no loading spinner)
        cacheCollectionJob = viewModelScope.launch {
            val cached = driveRepository.getCachedFiles(driveId, folderId).first()
            if (cached.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        files = cached,
                        isNavigating = false
                    )
                }
                fetchTmdbForFiles(cached)
            }
        }

        // 2) Check if we need a fresh API call (TTL-based)
        val lastLoaded = lastLoadedTimestamps[cacheKey]
        val now = System.currentTimeMillis()
        val needsFreshFetch = lastLoaded == null || (now - lastLoaded) > cacheTtlMs

        if (!needsFreshFetch) {
            // Cache is fresh enough, just show cached data
            viewModelScope.launch {
                val cached = driveRepository.getCachedFiles(driveId, folderId).first()
                _uiState.update {
                    it.copy(
                        files = cached,
                        isLoading = false,
                        isNavigating = false,
                        isRefreshing = false
                    )
                }
            }
            return
        }

        // 3) Fetch from API with subtle refresh indicator
        loadFilesJob = viewModelScope.launch {
            // Only show full loading if cache is empty
            val hasCachedData = _uiState.value.files.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hasCachedData,
                    isRefreshing = hasCachedData,
                    error = null
                )
            }

            driveRepository.listFilesInDrive(driveId, folderId).fold(
                onSuccess = { _ ->
                    lastLoadedTimestamps[cacheKey] = System.currentTimeMillis()
                    val freshFiles = driveRepository.getCachedFiles(driveId, folderId).first()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isNavigating = false,
                            files = freshFiles
                        )
                    }
                    fetchTmdbForFiles(freshFiles)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isNavigating = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    // ──── Home Tab ────

    fun loadHomeContent() {
        val movieFolders = appPreferences.tmdbMovieFolders
        val tvFolders = appPreferences.tmdbTvFolders
        val animeFolders = appPreferences.tmdbAnimeFolders
        val hasTmdb = appPreferences.tmdbApiKey.isNotEmpty()

        _uiState.update { it.copy(hasTmdbSetup = hasTmdb) }

        if (!hasTmdb) return
        if (movieFolders.isEmpty() && tvFolders.isEmpty() && animeFolders.isEmpty()) return
        // Wait for drives to be loaded before attempting to scan folders
        if (_uiState.value.sharedDrives.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isHomeLoading = true) }
            try {

            // Load files from each mapped folder set
            val movies = loadFilesFromFolders(movieFolders)
            val tvShows = loadFilesFromFolders(tvFolders)
            val anime = loadFilesFromFolders(animeFolders)

            _uiState.update {
                it.copy(
                    homeMovies = movies,
                    homeTvShows = tvShows,
                    homeAnime = anime,
                    isHomeLoading = false
                )
            }

            // Fetch TMDB metadata — batch-check cache first, only API-call missing ones
            val allFiles = (movies + tvShows + anime).distinctBy { it.id }
            val movieFileIds = movies.map { it.id }.toSet()
            val tvFileIds = tvShows.map { it.id }.toSet()
            val animeFileIds = anime.map { it.id }.toSet()

            // 1. Batch load cached metadata from DB
            val allIds = allFiles.map { it.id }
            val cachedMeta = tmdbRepository.getMetadataForFiles(allIds)
            val cachedMap = cachedMeta.associateBy { it.driveFileId }

            // Apply cached metadata immediately
            if (cachedMap.isNotEmpty()) {
                _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + cachedMap) }
            }

            // 2. Only fetch from API for files not yet cached
            val uncachedFiles = allFiles.filter { it.id !in cachedMap }
            uncachedFiles.forEach { file ->
                val type = when {
                    movieFileIds.contains(file.id) -> "movie"
                    tvFileIds.contains(file.id) -> "tv"
                    animeFileIds.contains(file.id) -> "tv"
                    else -> "auto"
                }

                // Check for .txt file with TMDB/IMDB ID inside the folder
                var meta: TmdbMetadataEntity? = null
                if (file.isFolder) {
                    val idFromTxt = detectMetadataIdInFolder(file)
                    if (idFromTxt != null) {
                        meta = tmdbRepository.fixMetadataById(file.id, idFromTxt, type)
                    }
                }

                // Fall back to name-based search
                if (meta == null) {
                    meta = tmdbRepository.fetchAndCacheMetadata(file.id, file.name, type)
                }

                meta?.let { m ->
                    _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + (file.id to m)) }
                }
            }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHomeLoading = false) }
            }
        }
    }

    /**
     * Load subfolders and direct video files from configured parent folders.
     * Each subfolder represents a single movie or TV show.
     * Direct video files (not in subfolders) are also included.
     * Sorted by modifiedTime newest first.
     */
    private suspend fun loadFilesFromFolders(folderIds: Set<String>): List<MediaFileEntity> {
        val result = mutableListOf<MediaFileEntity>()
        val drives = _uiState.value.sharedDrives

        folderIds.forEach { folderId ->
            var found = false
            // Try cache first across all drives
            for (drive in drives) {
                val cached = driveRepository.getCachedFiles(drive.id, folderId).first()
                if (cached.isNotEmpty()) {
                    result.addAll(cached)
                    found = true
                    break
                }
            }
            // If not cached, fetch from API (try each drive until one works)
            if (!found) {
                for (drive in drives) {
                    val apiResult = driveRepository.listFilesInDrive(drive.id, folderId)
                    if (apiResult.isSuccess) {
                        val fresh = driveRepository.getCachedFiles(drive.id, folderId).first()
                        result.addAll(fresh)
                        break
                    }
                }
            }
        }
        return result.distinctBy { it.id }
            .sortedByDescending { it.modifiedTime ?: "" }
    }

    // ──── TMDB ────

    private fun fetchTmdbForFiles(files: List<MediaFileEntity>) {
        if (!tmdbRepository.isConfigured()) return

        viewModelScope.launch {
            val movieFolders = appPreferences.tmdbMovieFolders
            val tvFolders = appPreferences.tmdbTvFolders
            val animeFolders = appPreferences.tmdbAnimeFolders
            val currentParent = _uiState.value.folderStack.lastOrNull()?.id
                ?: _uiState.value.selectedDrive?.id ?: return@launch

            val mediaType = when (currentParent) {
                in movieFolders -> "movie"
                in tvFolders -> "tv"
                in animeFolders -> "tv"
                else -> "auto"
            }

            val metadataMap = mutableMapOf<String, TmdbMetadataEntity>()
            files.forEach { file ->
                if (!file.isFolder) {
                    val metadata = tmdbRepository.fetchAndCacheMetadata(
                        driveFileId = file.id,
                        name = file.name,
                        mediaType = mediaType
                    )
                    metadata?.let { metadataMap[file.id] = it }
                }
            }

            _uiState.update {
                it.copy(tmdbMetadata = it.tmdbMetadata + metadataMap)
            }
        }
    }

    // ──── Search ────

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isNotBlank()) {
            viewModelScope.launch {
                when (_uiState.value.searchMode) {
                    SearchMode.CURRENT_DRIVE -> {
                        val driveId = _uiState.value.selectedDrive?.id ?: return@launch
                        driveRepository.searchFilesInDrive(query, driveId).first().let { results ->
                            _uiState.update { it.copy(files = results, searchResults = emptyMap()) }
                        }
                    }
                    SearchMode.ALL_DRIVES -> {
                        driveRepository.searchAllFiles(query).first().let { results ->
                            // Group by driveId
                            val grouped = results.groupBy { it.driveId }
                            _uiState.update {
                                it.copy(
                                    files = results,
                                    searchResults = grouped
                                )
                            }
                        }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyMap()) }
            refresh()
        }
    }

    fun setSearchMode(mode: SearchMode) {
        _uiState.update { it.copy(searchMode = mode) }
        // Re-run search if there's a query
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            updateSearchQuery(query)
        }
    }

    fun getDriveName(driveId: String): String {
        return _uiState.value.sharedDrives.find { it.id == driveId }?.name ?: driveId
    }

    // ──── Continue Playing ────

    private fun loadContinuePlaying() {
        viewModelScope.launch {
            playbackHistoryDao.getContinuePlaying(10).collect { items ->
                _uiState.update { it.copy(continuePlayingItems = items) }
            }
        }
    }

    // ──── Actions ────

    fun refresh() {
        val currentDrive = _uiState.value.selectedDrive ?: return
        val currentFolderId = _uiState.value.folderStack.lastOrNull()?.id
        // Force refresh by clearing the TTL cache for this key
        val cacheKey = "${currentDrive.id}/${currentFolderId ?: "root"}"
        lastLoadedTimestamps.remove(cacheKey)
        loadFiles(currentDrive.id, currentFolderId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            playbackHistoryDao.deleteAll()
            _uiState.update {
                it.copy(continuePlayingItems = emptyList())
            }
        }
    }

    fun removeFromHistory(fileId: String) {
        viewModelScope.launch {
            playbackHistoryDao.delete(fileId)
            // Refresh continue playing + last played lists
            loadContinuePlaying()
        }
    }

    /** Re-read all preference-backed state. Call when returning from Settings. */
    fun refreshPreferences() {
        _uiState.update {
            it.copy(
                selectedEngine = appPreferences.preferredEngine,
                isMpvAvailable = appPreferences.isMpvAvailable(),
                hasTmdbSetup = appPreferences.tmdbApiKey.isNotEmpty()
            )
        }
    }

    fun logout() {
        authRepository.logout()
    }

    /**
     * Check if a title folder contains a .txt file with a TMDB/IMDB ID as filename.
     * e.g. "12345.txt" → TMDB ID, "tt1234567.txt" → IMDB ID
     * Only called for uncached titles to avoid unnecessary API calls.
     */
    private suspend fun detectMetadataIdInFolder(folder: MediaFileEntity): String? {
        if (!folder.isFolder) return null

        val drives = _uiState.value.sharedDrives
        for (drive in drives) {
            // Use dedicated API call for text/plain files
            val txtFiles = driveRepository.listTextFilesInFolder(drive.id, folder.id)
            if (txtFiles.isEmpty()) continue

            for (txt in txtFiles) {
                val nameWithoutExt = txt.name.substringBeforeLast(".").trim()

                // IMDB ID: tt1234567
                if (nameWithoutExt.matches(Regex("""tt\d{5,}""", RegexOption.IGNORE_CASE))) {
                    return nameWithoutExt
                }

                // TMDB numeric ID: 12345
                if (nameWithoutExt.matches(Regex("""\d+"""))) {
                    return nameWithoutExt
                }

                // Prefixed: tmdb-12345 or imdb-tt1234567
                val prefixed = Regex("""(?:tmdb|imdb)[- _](.+)""", RegexOption.IGNORE_CASE).find(nameWithoutExt)
                if (prefixed != null) {
                    return prefixed.groupValues[1].trim()
                }
            }
            break // Found txt files from this drive, no need to try others
        }
        return null
    }
}
