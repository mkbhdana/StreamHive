package com.mkbhdana.streamhive.catalog

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import com.mkbhdana.streamhive.update.AppUpdateInfo
import com.mkbhdana.streamhive.update.AppUpdateRepository
import com.mkbhdana.streamhive.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { CURRENT_DRIVE, ALL_DRIVES }

data class TmdbCatalogSection(
    val folderId: String,
    val folderName: String,
    val typeLabel: String, // "Movie", "Series", "Anime"
    val mediaType: String, // "movie", "tv"
    val items: List<MediaFileEntity>
)

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
    val playFolderFilesExternally: Boolean = false,
    val isMpvAvailable: Boolean = false,
    val isNavigating: Boolean = false,

    // TMDB
    val tmdbMetadata: Map<String, TmdbMetadataEntity> = emptyMap(),

    // Home tab content
    val homeSections: List<TmdbCatalogSection> = emptyList(),
    val homeRecentlyAdded: List<MediaFileEntity> = emptyList(),
    val isHomeLoading: Boolean = false,
    val isHomeRefreshing: Boolean = false,
    val hasTmdbSetup: Boolean = false,

    // App Preferences
    val isGridView: Boolean = true,

    // Continue playing
    val continuePlayingItems: List<PlaybackHistoryEntity> = emptyList(),

    // Search Isolated Folder Navigation
    val searchFolderStack: List<SearchFolderInfo> = emptyList(),
    val searchFolderFiles: List<MediaFileEntity> = emptyList(),
    val isSearchFolderLoading: Boolean = false,

    // Connectivity
    val isOffline: Boolean = false,

    // App updates
    val availableUpdate: AppUpdateInfo? = null
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
    @ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val authRepository: AuthRepository,
    private val appPreferences: AppPreferences,
    private val tmdbRepository: TmdbRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaFileDao: MediaFileDao,
    @Suppress("unused") private val streamProxyServer: StreamProxyServer // ensures proxy starts early
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var loadFilesJob: Job? = null
    private var cacheCollectionJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val continueMetadataFetchAttempted = mutableSetOf<String>()

    // TTL cache: folderKey -> timestamp of last API fetch
    private val lastLoadedTimestamps = mutableMapOf<String, Long>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    init {
        _uiState.update {
            it.copy(
                selectedEngine = appPreferences.preferredEngine,
                isMpvAvailable = appPreferences.isMpvAvailable(),
                hasTmdbSetup = appPreferences.tmdbApiKey.isNotEmpty(),
                isGridView = appPreferences.isGridView
            )
        }
        observeNetworkConnectivity()
        loadSharedDrives()
        loadContinuePlaying()
    }

    /**
     * Monitor network connectivity changes. Updates [CatalogUiState.isOffline]
     * reactively and auto-refreshes drives when coming back online.
     */
    private fun observeNetworkConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        syncConnectivityState(cm)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                syncConnectivityState(cm)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                syncConnectivityState(cm)
            }

            override fun onLost(network: Network) {
                viewModelScope.launch {
                    delay(300)
                    syncConnectivityState(cm)
                }
            }

            override fun onUnavailable() {
                syncConnectivityState(cm)
            }
        }
        networkCallback = callback
        cm.registerDefaultNetworkCallback(callback)
    }

    private fun syncConnectivityState(cm: ConnectivityManager) {
        val wasOffline = _uiState.value.isOffline
        val isOnline = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        val isOffline = !isOnline

        _uiState.update { it.copy(isOffline = isOffline) }

        if (wasOffline && isOnline) {
            viewModelScope.launch {
                if (_uiState.value.sharedDrives.isEmpty()) {
                    loadSharedDrives()
                } else {
                    loadHomeContent(isRefresh = true)
                    _uiState.value.selectedDrive?.let { drive ->
                        loadFiles(drive.id, _uiState.value.folderStack.lastOrNull()?.id)
                    }
                }
            }
        }
    }

    fun toggleFolderExternalPlayback() {
        _uiState.update {
            it.copy(playFolderFilesExternally = !it.playFolderFilesExternally)
        }
    }

    fun toggleGridView() {
        val newValue = !_uiState.value.isGridView
        appPreferences.isGridView = newValue
        _uiState.update { it.copy(isGridView = newValue) }
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
                        it.copy(
                            isLoading = false,
                            error = if (_uiState.value.isOffline) null else error.message
                        )
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
                isNavigating = true,
                files = emptyList()
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

        _uiState.update { it.copy(folderStack = newStack, isNavigating = true, files = emptyList()) }
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
        _uiState.update { it.copy(folderStack = newStack, isNavigating = true, files = emptyList()) }
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
                isSearchFolderLoading = true,
                searchFolderFiles = emptyList()
            )
        }
        loadSearchFiles(driveId, folderId)
    }

    fun navigateBackSearchFolder(): Boolean {
        if (_uiState.value.isSearchFolderLoading) return false
        val currentStack = _uiState.value.searchFolderStack
        if (currentStack.isEmpty()) return false

        val newStack = currentStack.dropLast(1)
        
        _uiState.update { it.copy(searchFolderStack = newStack, isSearchFolderLoading = true, searchFolderFiles = emptyList()) }
        
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

        _uiState.update { it.copy(folderStack = emptyList(), isNavigating = true, files = emptyList()) }
        loadFiles(currentDrive.id, null)
    }

    private fun loadFiles(
        driveId: String,
        folderId: String?,
        showRefreshIndicator: Boolean = false
    ) {
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
                    isRefreshing = showRefreshIndicator && hasCachedData,
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
                            error = if (_uiState.value.isOffline) null else error.message
                        )
                    }
                }
            )
        }
    }

    // ──── Home Tab ────

    fun loadHomeContent(isRefresh: Boolean = false) {
        val movieFolders = appPreferences.tmdbMovieFolders
        val tvFolders = appPreferences.tmdbTvFolders
        val animeFolders = appPreferences.tmdbAnimeFolders
        val recentFolders = appPreferences.tmdbRecentFolders
        val hasTmdb = appPreferences.tmdbApiKey.isNotEmpty()

        _uiState.update { it.copy(hasTmdbSetup = hasTmdb) }

        if (!hasTmdb) {
            _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
            return
        }
        if (movieFolders.isEmpty() && tvFolders.isEmpty() && animeFolders.isEmpty() && recentFolders.isEmpty()) {
            _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
            return
        }

        val needsSpinner = _uiState.value.homeSections.isEmpty() && _uiState.value.homeRecentlyAdded.isEmpty()

        viewModelScope.launch {
            if (needsSpinner && !isRefresh) {
                _uiState.update { it.copy(isHomeLoading = true) }
            }
            try {
                val allFolders = driveRepository.getAllFolders().first()
                fun getFolderName(id: String) = allFolders.find { it.id == id }?.name ?: id

                val sections = mutableListOf<TmdbCatalogSection>()
                val allFetchedFiles = mutableListOf<MediaFileEntity>()

                // Build a type map for each folder
                val folderTypeMap = mutableMapOf<String, Pair<String, String>>() // folderId -> (typeLabel, mediaType)
                movieFolders.forEach { folderTypeMap[it] = "Movie" to "movie" }
                tvFolders.forEach { folderTypeMap[it] = "Series" to "tv" }
                animeFolders.forEach { folderTypeMap[it] = "Anime" to "tv" }

                // Use saved display order, appending any new folders not yet in order
                val savedOrder = appPreferences.tmdbFolderOrder
                val allFolderIds = folderTypeMap.keys.toList()
                val orderedIds = savedOrder.filter { it in allFolderIds }.toMutableList()
                allFolderIds.filter { it !in orderedIds }.forEach { orderedIds.add(it) }

                for (folderId in orderedIds) {
                    val (typeLabel, mediaType) = folderTypeMap[folderId] ?: continue
                    val files = loadFilesFromFolders(setOf(folderId))
                    if (files.isNotEmpty()) {
                        val sorted = files.sortedByDescending { it.modifiedTime ?: "" }
                        sections.add(TmdbCatalogSection(folderId, getFolderName(folderId), typeLabel, mediaType, sorted))
                        allFetchedFiles.addAll(files)
                    }
                }

                // Recently Added
                val recentFiles = mutableListOf<MediaFileEntity>()
                for (folderId in recentFolders) {
                    recentFiles.addAll(loadFilesFromFolders(setOf(folderId)))
                }
                val homeRecentlyAdded = recentFiles.distinctBy { it.id }
                    .sortedByDescending { it.createdTime ?: it.modifiedTime ?: "" }
                    .take(10)
                allFetchedFiles.addAll(homeRecentlyAdded)

                _uiState.update {
                    it.copy(
                        homeSections = sections,
                        homeRecentlyAdded = homeRecentlyAdded,
                        isHomeLoading = false,
                        isHomeRefreshing = false
                    )
                }

                // Fetch TMDB metadata
                val distinctFiles = allFetchedFiles.distinctBy { it.id }
                val allIds = distinctFiles.map { it.id }
                val cachedMeta = tmdbRepository.getMetadataForFiles(allIds)
                val cachedMap = cachedMeta.associateBy { it.driveFileId }

                if (cachedMap.isNotEmpty()) {
                    _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + cachedMap) }
                }

                val uncachedFiles = distinctFiles.filter { it.id !in cachedMap }
                
                // Determine mediaType mapping for fallback requests
                val movieIds = movieFolders.flatMap { loadFilesFromFolders(setOf(it)).map { f -> f.id } }.toSet()
                val tvIds = tvFolders.flatMap { loadFilesFromFolders(setOf(it)).map { f -> f.id } }.toSet()
                val animeIds = animeFolders.flatMap { loadFilesFromFolders(setOf(it)).map { f -> f.id } }.toSet()

                uncachedFiles.forEach { file ->
                    val type = when {
                        movieIds.contains(file.id) -> "movie"
                        tvIds.contains(file.id) -> "tv"
                        animeIds.contains(file.id) -> "tv"
                        else -> "auto"
                    }

                    var meta: TmdbMetadataEntity? = null
                    if (file.isFolder) {
                        val idFromName = detectMetadataIdInFolder(file)
                        if (idFromName != null) {
                            meta = tmdbRepository.fixMetadataById(file.id, idFromName, type)
                        }
                    }

                    if (meta == null) {
                        meta = tmdbRepository.fetchAndCacheMetadata(file.id, file.name, type)
                    }

                    meta?.let { m ->
                        _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + (file.id to m)) }
                    }
                }
                
                _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
            
            } catch (e: Exception) {
                _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
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
        val isOffline = _uiState.value.isOffline

        folderIds.forEach { folderId ->
            var found = false
            val localFolder = driveRepository.getFileById(folderId)
            val driveIdToUse = localFolder?.driveId?.takeIf { it.isNotBlank() }
                ?: appPreferences.selectedDriveId.takeIf { it.isNotBlank() }
                ?: drives.firstOrNull()?.id

            if (!isOffline && driveIdToUse != null) {
                // Check if we need fresh data (timestamp cache cleared or TTL expired)
                val cacheKey = "$driveIdToUse/$folderId"
                val lastLoaded = lastLoadedTimestamps[cacheKey]
                val now = System.currentTimeMillis()
                var needsFreshFetch = lastLoaded == null || (now - lastLoaded) > cacheTtlMs

                // Compare folder modified time if not already fetching
                if (!needsFreshFetch) {
                    val remoteModTime = driveRepository.getFolderModifiedTime(folderId, driveIdToUse)
                    if (remoteModTime != null && localFolder != null && remoteModTime != localFolder.modifiedTime) {
                        needsFreshFetch = true
                    } else if (remoteModTime != null && localFolder == null) {
                        needsFreshFetch = true
                    }
                }

                if (needsFreshFetch) {
                    val apiResult = driveRepository.listFilesInDrive(driveIdToUse, folderId)
                    if (apiResult.isSuccess) {
                        val fresh = driveRepository.getCachedFiles(driveIdToUse, folderId).first()
                        result.addAll(fresh)
                        found = true
                        lastLoadedTimestamps[cacheKey] = now
                    }
                }
            }

            // Fallback to cache if API not needed, failed, or offline
            if (!found && driveIdToUse != null) {
                val cached = driveRepository.getCachedFiles(driveIdToUse, folderId).first()
                if (cached.isNotEmpty()) {
                    result.addAll(cached)
                    found = true
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
        // Show the full home skeleton for explicit user refreshes.
        _uiState.update { it.copy(isHomeLoading = true, isHomeRefreshing = true, tmdbMetadata = emptyMap()) }
        // Clear folder timestamp cache to force API refresh
        lastLoadedTimestamps.clear()
        // Re-load home content from Drive API
        loadHomeContent(isRefresh = true)
    }

    // ──── Continue Playing ────

    private fun loadContinuePlaying() {
        viewModelScope.launch {
            playbackHistoryDao.getContinuePlaying(10).collect { items ->
                val continueMetadata = resolveContinuePlayingMetadata(items)
                _uiState.update {
                    it.copy(
                        continuePlayingItems = items,
                        tmdbMetadata = it.tmdbMetadata + continueMetadata
                    )
                }
            }
        }
    }

    // ──── Actions ────

    private suspend fun resolveContinuePlayingMetadata(
        items: List<PlaybackHistoryEntity>
    ): Map<String, TmdbMetadataEntity> {
        if (items.isEmpty()) return emptyMap()

        val metadataByFileId = mutableMapOf<String, TmdbMetadataEntity>()
        val directMetadata = tmdbRepository.getMetadataForFiles(items.map { it.fileId })
        directMetadata.forEach { metadata ->
            metadataByFileId[metadata.driveFileId] = metadata
        }

        val missingItems = items.filter { it.fileId !in metadataByFileId }
        if (missingItems.isNotEmpty()) {
            val filesById = missingItems.mapNotNull { item ->
                mediaFileDao.getFileById(item.fileId)?.let { file -> item.fileId to file }
            }.toMap()
            val parentIds = filesById.values.mapNotNull { it.parentId }.distinct()
            val parentMetadata = tmdbRepository.getMetadataForFiles(parentIds)
                .associateBy { it.driveFileId }

            filesById.forEach { (fileId, file) ->
                parentMetadata[file.parentId]?.let { metadata ->
                    metadataByFileId[fileId] = metadata
                }
            }

            if (tmdbRepository.isConfigured()) {
                filesById.forEach { (fileId, file) ->
                    if (fileId !in metadataByFileId && continueMetadataFetchAttempted.add(fileId)) {
                        val metadata = tmdbRepository.fetchAndCacheMetadata(
                            driveFileId = fileId,
                            name = file.name,
                            mediaType = inferMediaTypeForParent(file.parentId)
                        )
                        metadata?.let { metadataByFileId[fileId] = it }
                    }
                }
            }
        }

        items.forEach { item ->
            val metadata = metadataByFileId[item.fileId]
            if (item.posterPath.isNullOrBlank() && metadata?.posterPath != null) {
                playbackHistoryDao.upsert(item.copy(posterPath = metadata.posterPath))
            }
        }

        return metadataByFileId
    }

    private fun inferMediaTypeForParent(parentId: String?): String {
        return when {
            parentId != null && parentId in appPreferences.tmdbMovieFolders -> "movie"
            parentId != null && parentId in appPreferences.tmdbTvFolders -> "tv"
            parentId != null && parentId in appPreferences.tmdbAnimeFolders -> "tv"
            else -> "auto"
        }
    }

    fun refresh() {
        val currentDrive = _uiState.value.selectedDrive ?: return
        val currentFolderId = _uiState.value.folderStack.lastOrNull()?.id
        // Force refresh by clearing the TTL cache for this key
        val cacheKey = "${currentDrive.id}/${currentFolderId ?: "root"}"
        lastLoadedTimestamps.remove(cacheKey)
        loadFiles(currentDrive.id, currentFolderId, showRefreshIndicator = true)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun checkForAppUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val checkedRecently = now - appPreferences.lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS
        if (!force && checkedRecently) return
        if (!authRepository.isAuthenticated()) return
        if (!NetworkUtils.isNetworkAvailable(context)) return

        viewModelScope.launch {
            delay(2_500)
            if (!authRepository.isAuthenticated()) return@launch
            if (!NetworkUtils.isNetworkAvailable(context)) return@launch

            runCatching { appUpdateRepository.checkForUpdate() }
                .getOrElse { error -> Result.failure<AppUpdateInfo?>(error) }
                .fold(
                    onSuccess = { update ->
                        appPreferences.lastUpdateCheckAt = now
                        if (update != null && appPreferences.dismissedUpdateTag != update.tagName) {
                            _uiState.update { it.copy(availableUpdate = update) }
                        }
                    },
                    onFailure = {
                        // Ignore update check failures; playback/catalog should not be blocked by GitHub.
                    }
                )
        }
    }

    fun dismissUpdatePrompt(suppressThisVersion: Boolean = false) {
        val update = _uiState.value.availableUpdate
        if (suppressThisVersion && update != null) {
            appPreferences.dismissedUpdateTag = update.tagName
        }
        _uiState.update { it.copy(availableUpdate = null) }
    }

    override fun onCleared() {
        super.onCleared()
        val callback = networkCallback ?: return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        networkCallback = null
        connectivityManager = null
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
                hasTmdbSetup = appPreferences.tmdbApiKey.isNotEmpty(),
                isGridView = appPreferences.isGridView
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

    private companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
