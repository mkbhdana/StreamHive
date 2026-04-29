package com.mkbhdana.streamhive.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.data.model.SharedDrive
import com.mkbhdana.streamhive.data.tmdb.TmdbRepository
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { CURRENT_DRIVE, ALL_DRIVES }

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
    val currentDriveSearchResults: List<MediaFileEntity> = emptyList(), // For CURRENT_DRIVE mode
    val isSearchLoading: Boolean = false,
    val error: String? = null,
    val selectedEngine: PlayerEngine = PlayerEngine.EXO_PLAYER,
    val isMpvAvailable: Boolean = false,
    val isNavigating: Boolean = false,

    // TMDB
    val tmdbMetadata: Map<String, TmdbMetadataEntity> = emptyMap(),

    // Home tab content
    val homeMovies: List<MediaFileEntity> = emptyList(),
    val homeTvShows: List<MediaFileEntity> = emptyList(),
    val homeAnime: List<MediaFileEntity> = emptyList(),
    val isHomeLoading: Boolean = false,
    val hasTmdbSetup: Boolean = false,

    // Continue playing
    val continuePlayingItems: List<PlaybackHistoryEntity> = emptyList(),

    // Search Isolated Folder Navigation
    val searchFolderStack: List<SearchFolderInfo> = emptyList(),
    val searchFolderFiles: List<MediaFileEntity> = emptyList(),
    val isSearchFolderLoading: Boolean = false
)

data class SearchFolderInfo(
    val id: String,
    val name: String,
    val driveId: String
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
    private val mediaFileDao: MediaFileDao,
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
                    // Restore saved drive
                    val savedDriveId = appPreferences.selectedDriveId
                    val restoredDrive = if (savedDriveId.isNotEmpty()) {
                        drives.find { it.id == savedDriveId }
                    } else null
                    val selectedDrive = restoredDrive ?: drives.firstOrNull()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sharedDrives = drives,
                            selectedDrive = selectedDrive
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
                folderStack = emptyList(),
                files = emptyList(),
                isLoading = true
            )
        }
        loadFiles(drive.id, null)
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

    // ──── Search Isolated Folder Navigation ────

    fun openSearchFolder(folderId: String, folderName: String, driveId: String) {
        if (_uiState.value.isSearchFolderLoading) return
        val currentStack = _uiState.value.searchFolderStack
        if (currentStack.isNotEmpty() && currentStack.last().id == folderId) return

        _uiState.update {
            it.copy(
                searchFolderStack = it.searchFolderStack + SearchFolderInfo(folderId, folderName, driveId),
                isSearchFolderLoading = true
            )
        }
        loadSearchFiles(driveId, folderId)
    }

    fun navigateBackSearchFolder(): Boolean {
        if (_uiState.value.isSearchFolderLoading) return false
        val currentStack = _uiState.value.searchFolderStack
        if (currentStack.isEmpty()) return false

        val newStack = currentStack.dropLast(1)
        
        _uiState.update { it.copy(searchFolderStack = newStack, isSearchFolderLoading = true) }
        
        if (newStack.isEmpty()) {
            _uiState.update { it.copy(searchFolderFiles = emptyList(), isSearchFolderLoading = false) }
        } else {
            val parentFolder = newStack.last()
            loadSearchFiles(parentFolder.driveId, parentFolder.id)
        }
        return true
    }

    fun navigateToSearchFolderIndex(index: Int) {
        if (_uiState.value.isSearchFolderLoading) return
        val currentStack = _uiState.value.searchFolderStack
        if (index < 0 || index >= currentStack.size) return
        if (index == currentStack.lastIndex) return

        val newStack = currentStack.take(index + 1)
        _uiState.update { it.copy(searchFolderStack = newStack, isSearchFolderLoading = true) }
        val targetFolder = newStack.last()
        loadSearchFiles(targetFolder.driveId, targetFolder.id)
    }

    fun clearSearchFolderStack() {
        _uiState.update {
            it.copy(
                searchFolderStack = emptyList(),
                searchFolderFiles = emptyList(),
                isSearchFolderLoading = false
            )
        }
    }

    private var searchFolderLoadJob: Job? = null

    private fun loadSearchFiles(driveId: String, folderId: String) {
        searchFolderLoadJob?.cancel()
        searchFolderLoadJob = viewModelScope.launch {
            // First show cached files if available
            val cached = driveRepository.getCachedFiles(driveId, folderId).first()
            if (cached.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        searchFolderFiles = cached,
                        isSearchFolderLoading = false
                    )
                }
            }

            // Always fetch fresh from API since search folders aren't TTL cached here
            _uiState.update { it.copy(isSearchFolderLoading = true) }
            val apiResult = driveRepository.listFilesInDrive(driveId, folderId)
            if (apiResult.isSuccess) {
                val fresh = driveRepository.getCachedFiles(driveId, folderId).first()
                _uiState.update {
                    it.copy(
                        searchFolderFiles = fresh,
                        isSearchFolderLoading = false
                    )
                }
                fetchTmdbForFiles(fresh)
            } else {
                _uiState.update { it.copy(isSearchFolderLoading = false) }
            }
        }
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

        val needsSpinner = _uiState.value.homeMovies.isEmpty() &&
                _uiState.value.homeTvShows.isEmpty() &&
                _uiState.value.homeAnime.isEmpty()

        viewModelScope.launch {
            if (needsSpinner) {
                _uiState.update { it.copy(isHomeLoading = true) }
            }
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

                // Check for TMDB/IMDB ID in the folder name
                var meta: TmdbMetadataEntity? = null
                if (file.isFolder) {
                    val idFromName = detectMetadataIdInFolder(file)
                    if (idFromName != null) {
                        meta = tmdbRepository.fixMetadataById(file.id, idFromName, type)
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
            
            _uiState.update { it.copy(isHomeLoading = false) }
            
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
     *
     * Compares cache folder modifiedTime with Drive API to detect changes.
     * Always fetches from API if timestamp cache is cleared (e.g. after refresh).
     */
    private suspend fun loadFilesFromFolders(folderIds: Set<String>): List<MediaFileEntity> {
        val result = mutableListOf<MediaFileEntity>()
        val drives = _uiState.value.sharedDrives

        folderIds.forEach { folderId ->
            var found = false
            // Check if we need fresh data (timestamp cache cleared or TTL expired)
            val cacheKey = "${drives.firstOrNull()?.id ?: "unknown"}/$folderId"
            val lastLoaded = lastLoadedTimestamps[cacheKey]
            val now = System.currentTimeMillis()
            var needsFreshFetch = lastLoaded == null || (now - lastLoaded) > cacheTtlMs

            // Compare folder modified time if not already fetching
            if (!needsFreshFetch) {
                val driveId = drives.firstOrNull()?.id
                if (driveId != null) {
                    val remoteModTime = driveRepository.getFolderModifiedTime(folderId, driveId)
                    val localFolder = driveRepository.getFileById(folderId)
                    if (remoteModTime != null && localFolder != null && remoteModTime != localFolder.modifiedTime) {
                        needsFreshFetch = true
                    } else if (remoteModTime != null && localFolder == null) {
                        // We don't even have the folder locally
                        needsFreshFetch = true
                    }
                }
            }

            if (needsFreshFetch) {
                // Always try API first when cache is stale, cleared, or modified
                for (drive in drives) {
                    val apiResult = driveRepository.listFilesInDrive(drive.id, folderId)
                    if (apiResult.isSuccess) {
                        val fresh = driveRepository.getCachedFiles(drive.id, folderId).first()
                        result.addAll(fresh)
                        found = true
                        lastLoadedTimestamps[cacheKey] = now
                        break
                    }
                }
            }

            // Fallback to cache if API not needed or failed
            if (!found) {
                for (drive in drives) {
                    val cached = driveRepository.getCachedFiles(drive.id, folderId).first()
                    if (cached.isNotEmpty()) {
                        result.addAll(cached)
                        found = true
                        break
                    }
                }
            }
        }
        return result.distinctBy { it.id }
            .sortedByDescending { it.createdTime ?: it.modifiedTime ?: "" }
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

    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(600)
                _uiState.update { it.copy(currentDriveSearchResults = emptyList(), searchResults = emptyMap(), isSearchLoading = true) }
                when (_uiState.value.searchMode) {
                    SearchMode.CURRENT_DRIVE -> {
                        val driveId = _uiState.value.selectedDrive?.id ?: return@launch
                        // Search Drive API directly for fresh results, then cache to DB
                        val apiResult = driveRepository.searchFilesViaApi(query, driveId)
                        if (apiResult.isSuccess) {
                            // Cache results to database
                            val entities = apiResult.getOrNull()?.map { file ->
                                MediaFileEntity(
                                    id = file.id,
                                    name = file.name,
                                    mimeType = file.mimeType,
                                    size = file.size,
                                    thumbnailLink = file.thumbnailLink,
                                    modifiedTime = file.modifiedTime,
                                    createdTime = file.createdTime,
                                    parentId = file.parents?.firstOrNull() ?: driveId,
                                    driveId = driveId,
                                    fileExtension = file.fileExtension,
                                    isFolder = file.isFolder
                                )
                            } ?: emptyList()
                            // Results are no longer cached to database to prevent polluting Folder tab
                            _uiState.update {
                                it.copy(
                                    currentDriveSearchResults = entities,
                                    searchResults = emptyMap()
                                )
                            }
                        } else {
                            // Fallback to cache if API fails
                            driveRepository.searchFilesInDrive(query, driveId).first().let { results ->
                                _uiState.update { it.copy(currentDriveSearchResults = results, searchResults = emptyMap()) }
                            }
                        }
                        _uiState.update { it.copy(isSearchLoading = false) }
                    }
                    SearchMode.ALL_DRIVES -> {
                        // Search Drive API directly across all drives
                        val apiResult = driveRepository.searchFilesViaApi(query, null)
                        if (apiResult.isSuccess) {
                            val results = apiResult.getOrNull()?.map { file ->
                                MediaFileEntity(
                                    id = file.id,
                                    name = file.name,
                                    mimeType = file.mimeType,
                                    size = file.size,
                                    thumbnailLink = file.thumbnailLink,
                                    modifiedTime = file.modifiedTime,
                                    createdTime = file.createdTime,
                                    parentId = file.parents?.firstOrNull() ?: file.driveId ?: "",
                                    driveId = file.driveId ?: "",
                                    fileExtension = file.fileExtension,
                                    isFolder = file.isFolder
                                )
                            } ?: emptyList()
                            // Results are no longer cached to database to prevent polluting Folder tab
                            // Group by driveId
                            val grouped = results.groupBy { it.driveId }
                            _uiState.update {
                                it.copy(
                                    currentDriveSearchResults = results,
                                    searchResults = grouped
                                )
                            }
                        } else {
                            // Fallback to cache if API fails
                            driveRepository.searchAllFiles(query).first().let { results ->
                                val grouped = results.groupBy { it.driveId }
                                _uiState.update {
                                    it.copy(
                                        currentDriveSearchResults = results,
                                        searchResults = grouped
                                    )
                                }
                            }
                        }
                        _uiState.update { it.copy(isSearchLoading = false) }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyMap(), currentDriveSearchResults = emptyList()) }
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
        if (driveId.isBlank()) return "Unknown Drive"
        if (driveId == "system_root") return "My Drive"
        return _uiState.value.sharedDrives.find { it.id == driveId }?.name ?: driveId
    }

    /** Force-refresh home content, clearing folder cache timestamps and TMDB metadata */
    fun refreshHomeContent() {
        // Clear TMDB metadata cache
        _uiState.update { it.copy(tmdbMetadata = emptyMap()) }
        // Clear folder timestamp cache to force API refresh
        lastLoadedTimestamps.clear()
        // Re-load home content from Drive API
        loadHomeContent()
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
            removeFromHistorySync(fileId)
        }
    }

    suspend fun removeFromHistorySync(fileId: String) {
        playbackHistoryDao.delete(fileId)
        loadContinuePlaying()
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

    private fun detectMetadataIdInFolder(folder: MediaFileEntity): String? {
        if (!folder.isFolder) return null

        // Look for ID in the folder name, e.g. "Movie Name [tt1234567]" or "Movie Name [12345]"
        val idMatch = Regex("""\[(tt\d+|\d+)\]""", RegexOption.IGNORE_CASE).find(folder.name)
        if (idMatch != null) {
            return idMatch.groupValues[1]
        }
        return null
    }
}
