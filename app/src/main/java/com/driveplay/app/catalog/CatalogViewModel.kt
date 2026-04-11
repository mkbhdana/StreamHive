package com.driveplay.app.catalog

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
import com.driveplay.app.settings.AppPreferences
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
    private val playbackHistoryDao: PlaybackHistoryDao
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
            PlayerEngine.MPV -> PlayerEngine.EXO_PLAYER
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
                files = emptyList()
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

        viewModelScope.launch {
            _uiState.update { it.copy(isHomeLoading = true) }

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

            // Fetch TMDB metadata for all home items
            (movies + tvShows + anime).forEach { file ->
                val type = when {
                    file.parentId in movieFolders -> "movie"
                    file.parentId in tvFolders -> "tv"
                    file.parentId in animeFolders -> "tv"
                    else -> "auto"
                }
                val meta = tmdbRepository.fetchAndCacheMetadata(file.id, file.name, type)
                meta?.let { m ->
                    _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + (file.id to m)) }
                }
            }
        }
    }

    private suspend fun loadFilesFromFolders(folderIds: Set<String>): List<MediaFileEntity> {
        val result = mutableListOf<MediaFileEntity>()
        folderIds.forEach { folderId ->
            // We need the driveId – search all drives for this folder
            val drives = _uiState.value.sharedDrives
            drives.forEach { drive ->
                val cached = driveRepository.getCachedFiles(drive.id, folderId).first()
                if (cached.isNotEmpty()) {
                    result.addAll(cached)
                } else {
                    // Try to load from API
                    driveRepository.listFilesInDrive(drive.id, folderId).onSuccess {
                        val fresh = driveRepository.getCachedFiles(drive.id, folderId).first()
                        result.addAll(fresh)
                    }
                }
            }
        }
        return result
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

    fun logout() {
        authRepository.logout()
    }
}
