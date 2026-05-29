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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.SharedPreferences

enum class SearchMode { CURRENT_DRIVE, ALL_DRIVES }

data class TmdbCatalogSection(
    val folderId: String,
    val folderName: String,
    val typeLabel: String, // "Movie" or "Series"
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
    val searchMode: SearchMode = SearchMode.ALL_DRIVES,
    val isExactSearch: Boolean = false,
    val searchResults: Map<String, List<MediaFileEntity>> = emptyMap(), // driveId -> files for grouped search
    val currentDriveSearchResults: List<MediaFileEntity> = emptyList(),
    val tmdbSearchResults: List<MediaFileEntity> = emptyList(),
    val isSearchLoading: Boolean = false,
    val error: String? = null,
    val playFolderFilesExternally: Boolean = false,
    val isMpvAvailable: Boolean = false,
    val isNavigating: Boolean = false,
    val isOAuthUser: Boolean = false,

    // TMDB
    val tmdbMetadata: Map<String, TmdbMetadataEntity> = emptyMap(),

    // Home tab content
    val homeSections: List<TmdbCatalogSection> = emptyList(),
    val homeRecentlyAdded: List<MediaFileEntity> = emptyList(),
    val isHomeLoading: Boolean = false,
    val isHomeRefreshing: Boolean = false,
    val hasTmdbSetup: Boolean = false,
    val tmdbConfiguredFolderIds: Set<String> = emptySet(),

    // App Preferences
    val isGridView: Boolean = true,

    // Continue playing
    val continuePlayingItems: List<PlaybackHistoryEntity> = emptyList(),

    // Search Isolated Folder Navigation
    val searchFolderStack: List<SearchFolderInfo> = emptyList(),
    val searchFolderFiles: List<MediaFileEntity> = emptyList(),
    val isSearchFolderLoading: Boolean = false,

    // Connectivity
    val isOffline: Boolean = false
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
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaFileDao: MediaFileDao,
    @Suppress("unused") private val streamProxyServer: StreamProxyServer // ensures proxy starts early
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    fun getPreferredEngine(): PlayerEngine = appPreferences.preferredEngine

    private var loadFilesJob: Job? = null
    private var cacheCollectionJob: Job? = null
    private var homeLoadJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var offlineDebounceJob: Job? = null
    private val continueMetadataFetchAttempted = mutableSetOf<String>()
    private var lastHomeLoadTimestamp = 0L

    // TTL cache: folderKey -> timestamp of last API fetch
    private val lastLoadedTimestamps = mutableMapOf<String, Long>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPreferences.KEY_CATALOG_SETTINGS_LAST_CHANGED) {
            loadHomeContent(isRefresh = true)
        }
    }

    init {
        val hasTmdb = appPreferences.tmdbApiKey.isNotEmpty()
        _uiState.update {
            it.copy(
                isMpvAvailable = appPreferences.isMpvAvailable(),
                hasTmdbSetup = hasTmdb,
                tmdbConfiguredFolderIds = appPreferences.tmdbMovieFolders + appPreferences.tmdbTvFolders + appPreferences.tmdbRecentFolders,
                isGridView = appPreferences.isGridView,
                // Show skeleton from first render so continue-playing doesn't flash alone
                isHomeLoading = hasTmdb
            )
        }
        observeNetworkConnectivity()
        appPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)

        viewModelScope.launch {
            authRepository.authState.collect { state ->
                if (state is com.mkbhdana.streamhive.data.model.AuthState.Unauthenticated) {
                    val tmdbKeyExists = appPreferences.tmdbApiKey.isNotEmpty()
                    _uiState.update { CatalogUiState(
                        isMpvAvailable = appPreferences.isMpvAvailable(),
                        hasTmdbSetup = tmdbKeyExists,
                        tmdbConfiguredFolderIds = appPreferences.tmdbMovieFolders + appPreferences.tmdbTvFolders + appPreferences.tmdbRecentFolders,
                        isGridView = appPreferences.isGridView,
                        isHomeLoading = tmdbKeyExists
                    ) }
                    lastLoadedTimestamps.clear()
                    continueMetadataFetchAttempted.clear()
                } else if (state is com.mkbhdana.streamhive.data.model.AuthState.Authenticated) {
                    val isOAuth = state.credentials is com.mkbhdana.streamhive.data.model.AuthCredentials.OAuth2Credentials
                    _uiState.update { it.copy(isOAuthUser = isOAuth) }
                    loadSharedDrives()
                    loadContinuePlaying()
                }
            }
        }
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

        if (isOnline) {
            // Going online: apply immediately, cancel any pending offline debounce
            offlineDebounceJob?.cancel()
            offlineDebounceJob = null
            _uiState.update { it.copy(isOffline = false) }

            if (wasOffline) {
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
        } else {
            // Going offline: debounce to avoid false positives during activity transitions
            if (!wasOffline && offlineDebounceJob == null) {
                offlineDebounceJob = viewModelScope.launch {
                    delay(1500)
                    _uiState.update { it.copy(isOffline = true) }
                    offlineDebounceJob = null
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
                    val selectedDrive = _uiState.value.selectedDrive
                        ?.let { selected -> drives.find { it.id == selected.id } }
                    if (selectedDrive == null) {
                        appPreferences.selectedDriveId = ""
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sharedDrives = drives,
                            selectedDrive = selectedDrive,
                            searchMode = if (selectedDrive == null) SearchMode.ALL_DRIVES else SearchMode.CURRENT_DRIVE
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
                isLoading = true,
                searchMode = SearchMode.CURRENT_DRIVE
            )
        }
        loadFiles(drive.id, null)
    }

    fun clearSelectedDrive() {
        appPreferences.selectedDriveId = ""
        _uiState.update {
            it.copy(
                selectedDrive = null,
                folderStack = emptyList(),
                files = emptyList(),
                searchMode = SearchMode.ALL_DRIVES
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
                isNavigating = true,
                files = emptyList(),
                searchMode = SearchMode.CURRENT_DRIVE
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
        // Don't attempt to load home content if drives haven't been fetched yet.
        // loadSharedDrives() will call this again once drives are available.
        val settingsChanged = lastHomeLoadTimestamp <= appPreferences.catalogSettingsLastChanged
        val effectiveIsRefresh = isRefresh || settingsChanged
        if (_uiState.value.sharedDrives.isEmpty() && !effectiveIsRefresh) return

        val movieFolders = appPreferences.tmdbMovieFolders
        val tvFolders = appPreferences.tmdbTvFolders
        val recentFolders = appPreferences.tmdbRecentFolders
        val hasTmdb = appPreferences.tmdbApiKey.isNotEmpty()

        _uiState.update { it.copy(
            hasTmdbSetup = hasTmdb,
            tmdbConfiguredFolderIds = movieFolders + tvFolders + recentFolders
        ) }

        if (!hasTmdb) {
            homeLoadJob?.cancel()
            lastLoadedTimestamps.clear()
            lastHomeLoadTimestamp = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    homeSections = emptyList(),
                    homeRecentlyAdded = emptyList(),
                    isHomeLoading = false,
                    isHomeRefreshing = false
                )
            }
            return
        }
        if (
            movieFolders.isEmpty() &&
            tvFolders.isEmpty() &&
            recentFolders.isEmpty()
        ) {
            homeLoadJob?.cancel()
            lastLoadedTimestamps.clear()
            lastHomeLoadTimestamp = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    homeSections = emptyList(),
                    homeRecentlyAdded = emptyList(),
                    isHomeLoading = false,
                    isHomeRefreshing = false
                )
            }
            return
        }

        // Debounce: skip reload if data was loaded recently and we already have content
        val hasContent = _uiState.value.homeSections.isNotEmpty() || _uiState.value.homeRecentlyAdded.isNotEmpty()
        val now = System.currentTimeMillis()
        
        if (!effectiveIsRefresh && hasContent && (now - lastHomeLoadTimestamp) < HOME_CONTENT_DEBOUNCE_MS) {
            // Content is fresh, don't reload or show skeleton
            _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
            return
        }

        // Show skeleton if there's no content yet, or if settings changed (forcing a hard refresh)
        if (!hasContent || settingsChanged) {
            if (settingsChanged) {
                lastLoadedTimestamps.clear()
                lastHomeLoadTimestamp = 0L
            }
            _uiState.update { 
                it.copy(
                    isHomeLoading = true,
                    homeSections = if (settingsChanged) emptyList() else it.homeSections,
                    homeRecentlyAdded = if (settingsChanged) emptyList() else it.homeRecentlyAdded
                ) 
            }
        }

        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            try {
                val allFolders = driveRepository.getAllFolders().first()
                fun getFolderName(id: String) = allFolders.find { it.id == id }?.name ?: id

                val sections = mutableListOf<TmdbCatalogSection>()
                val allFetchedFiles = mutableListOf<MediaFileEntity>()

                // Build a type map for each folder
                val folderTypeMap = mutableMapOf<String, Pair<String, String>>() // folderId -> (typeLabel, mediaType)
                movieFolders.forEach { folderTypeMap[it] = "Movie" to "movie" }
                tvFolders.forEach { folderTypeMap[it] = "Series" to "tv" }

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

                lastHomeLoadTimestamp = System.currentTimeMillis()
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

                val missingOriginalLanguageIds = cachedMap
                    .filterValues { it.originalLanguage.isNullOrBlank() }
                    .keys
                val uncachedFiles = distinctFiles.filter {
                    it.id !in cachedMap || it.id in missingOriginalLanguageIds
                }
                
                // Build mediaType lookup from already-fetched section data (no extra API calls)
                val fileTypeMap = mutableMapOf<String, String>()
                sections.forEach { section ->
                    section.items.forEach { file -> fileTypeMap[file.id] = section.mediaType }
                }

                // Fetch uncached TMDB metadata in parallel batches
                // Add delay between batches to avoid TMDB rate limiting (429)
                uncachedFiles.chunked(5).forEachIndexed { index, batch ->
                    if (index > 0) delay(300)
                    coroutineScope {
                        val results = batch.map { file ->
                            async {
                                val type = fileTypeMap[file.id] ?: "auto"

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

                                if (meta != null) file.id to meta else null
                            }
                        }.awaitAll().filterNotNull()

                        if (results.isNotEmpty()) {
                            _uiState.update {
                                it.copy(tmdbMetadata = it.tmdbMetadata + results.toMap())
                            }
                        }
                    }
                }
                
                _uiState.update { it.copy(isHomeLoading = false, isHomeRefreshing = false) }
            
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
            val metadataMap = mutableMapOf<String, TmdbMetadataEntity>()
            files.forEach { file ->
                if (!file.isFolder) {
                    val mediaType = inferMediaTypeForParent(file.parentId)
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
                performSearch(query)
            }
        } else {
            _uiState.update {
                it.copy(
                    currentDriveSearchResults = emptyList(),
                    searchResults = emptyMap(),
                    tmdbSearchResults = emptyList(),
                    isSearchLoading = false
                )
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(currentDriveSearchResults = emptyList(), searchResults = emptyMap(), tmdbSearchResults = emptyList(), isSearchLoading = true) }

        // TMDB catalog search is independent from Drive API/current folder scope.
        // It only searches cached TMDB metadata for user-added catalog folders.
        val tmdbMatches = searchTmdbCatalog(query)
        _uiState.update { it.copy(tmdbSearchResults = tmdbMatches) }

        when (_uiState.value.searchMode) {
            SearchMode.CURRENT_DRIVE -> {
                val state = _uiState.value
                val driveId = state.selectedDrive?.id ?: run {
                    _uiState.update { it.copy(searchMode = SearchMode.ALL_DRIVES, isSearchLoading = false) }
                    return
                }
                val folderId = state.folderStack.lastOrNull()?.id
                android.util.Log.d("SearchDebug", "performSearch CURRENT_DRIVE: driveId=$driveId, folderId=$folderId, query=$query")
                // Search Drive API directly for fresh results. If a folder is open,
                // search only that folder tree.
                val apiResult = driveRepository.searchFilesViaApi(query, driveId, folderId)
                if (apiResult.isSuccess) {
                    android.util.Log.d("SearchDebug", "API call succeeded for CURRENT_DRIVE")
                    val entities = apiResult.getOrNull()?.map { file ->
                        MediaFileEntity(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            size = file.size,
                            thumbnailLink = file.thumbnailLink,
                            modifiedTime = file.modifiedTime,
                            createdTime = file.createdTime,
                            parentId = file.parents?.firstOrNull() ?: folderId ?: driveId,
                            driveId = driveId,
                            fileExtension = file.fileExtension,
                            isFolder = file.isFolder
                        )
                    } ?: emptyList()
                    val filtered = applyExactFilter(entities, query)
                    fetchTmdbForFiles(filtered)
                    _uiState.update {
                        it.copy(
                            currentDriveSearchResults = filtered,
                            searchResults = emptyMap(),
                            tmdbSearchResults = tmdbMatches
                        )
                    }
                } else {
                    android.util.Log.e("SearchDebug", "API call failed for CURRENT_DRIVE. Error: ${apiResult.exceptionOrNull()?.message}", apiResult.exceptionOrNull())
                    // Fallback to cache if API fails
                    searchCachedCurrentPath(query, driveId, folderId).let { results ->
                        android.util.Log.d("SearchDebug", "Fallback cache returned ${results.size} files")
                        val filtered = applyExactFilter(results, query)
                        fetchTmdbForFiles(filtered)
                        _uiState.update { it.copy(currentDriveSearchResults = filtered, searchResults = emptyMap(), tmdbSearchResults = tmdbMatches) }
                    }
                }
                _uiState.update { it.copy(isSearchLoading = false) }
            }
            SearchMode.ALL_DRIVES -> {
                android.util.Log.d("SearchDebug", "performSearch ALL_DRIVES: query=$query")
                // Search Drive API directly across all drives
                val apiResult = driveRepository.searchFilesViaApi(query, null)
                if (apiResult.isSuccess) {
                    android.util.Log.d("SearchDebug", "API call succeeded for ALL_DRIVES")
                    val results = apiResult.getOrNull()?.map { file ->
                        val resolvedDriveId = file.driveId?.takeIf { it.isNotBlank() } ?: "my_drive"
                        MediaFileEntity(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            size = file.size,
                            thumbnailLink = file.thumbnailLink,
                            modifiedTime = file.modifiedTime,
                            createdTime = file.createdTime,
                            parentId = file.parents?.firstOrNull() ?: resolvedDriveId,
                            driveId = resolvedDriveId,
                            fileExtension = file.fileExtension,
                            isFolder = file.isFolder
                        )
                    } ?: emptyList()
                    // Results are no longer cached to database to prevent polluting Folder tab
                    // Group by driveId
                    val filtered = applyExactFilter(results, query)
                    fetchTmdbForFiles(filtered)
                    val grouped = filtered.groupBy { it.driveId }
                    _uiState.update {
                        it.copy(
                            currentDriveSearchResults = filtered,
                            searchResults = grouped
                        )
                    }
                } else {
                    android.util.Log.e("SearchDebug", "API call failed for ALL_DRIVES. Error: ${apiResult.exceptionOrNull()?.message}", apiResult.exceptionOrNull())
                    // Fallback to cache if API fails
                    val results = driveRepository.searchAllFiles(query).first()
                    android.util.Log.d("SearchDebug", "Fallback cache returned ${results.size} files")
                    val filtered = applyExactFilter(results, query)
                    fetchTmdbForFiles(filtered)
                    val grouped = filtered.groupBy { it.driveId }
                    _uiState.update {
                        it.copy(
                            currentDriveSearchResults = filtered,
                            searchResults = grouped
                        )
                    }
                }
                _uiState.update { it.copy(isSearchLoading = false) }
            }
        }
    }

    fun setSearchMode(mode: SearchMode) {
        val resolvedMode = if (mode == SearchMode.CURRENT_DRIVE && _uiState.value.selectedDrive == null) {
            SearchMode.ALL_DRIVES
        } else {
            mode
        }
        _uiState.update { it.copy(searchMode = resolvedMode) }
        // Re-run search if there's a query
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(query)
            }
        }
    }

    fun toggleExactSearch() {
        _uiState.update { it.copy(isExactSearch = !it.isExactSearch) }
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(query)
            }
        }
    }

    /** 
     * Media-aware Exact phrase filter.
     * Handles cases where files use dots/punctuation (e.g. "Look.Back.2024.mkv" matches "Look Back")
     * but prevents "From" from matching "From.Ground.Zero" by analyzing the word immediately following the query.
     */
    private fun applyExactFilter(files: List<MediaFileEntity>, query: String): List<MediaFileEntity> {
        if (!_uiState.value.isExactSearch) return files
        
        // Split query into words
        val queryTokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return files
        
        return files.filter { file ->
            // Split filename into words
            val fileTokens = file.name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            
            // File must have at least as many words as the query
            if (fileTokens.size < queryTokens.size) return@filter false
            
            // Check if the filename starts with the exact query words
            var startsWithQuery = true
            for (i in queryTokens.indices) {
                if (fileTokens[i] != queryTokens[i]) {
                    startsWithQuery = false
                    break
                }
            }
            
            if (!startsWithQuery) return@filter false
            
            // If the query is the entire filename, it's an exact match
            if (fileTokens.size == queryTokens.size) return@filter true
            
            // Look at the very next word in the filename after the title
            val nextWord = fileTokens[queryTokens.size]
            
            // Is it a known media metadata tag?
            val isYear = nextWord.matches(Regex("^(19|20)\\d{2}$"))
            val isSeasonEpisode = nextWord.matches(Regex("^(s\\d{1,2}|e\\d{1,2}|season|episode|ep|part|pt).*"))
            val isResolution = nextWord.matches(Regex("^(1080p|720p|4k|2160p|480p)$"))
            val isExtension = nextWord.matches(Regex("^(mkv|mp4|avi|srt|sub|txt|jpg|png)$"))
            
            // If the next word is metadata, the query was exactly the title.
            // If it's a regular word (like "ground" in "From Ground Zero"), the title isn't finished yet.
            isYear || isSeasonEpisode || isResolution || isExtension
        }
    }

    fun prepareSearchForCurrentLibraryPath() {
        val mode = if (_uiState.value.selectedDrive == null) {
            SearchMode.ALL_DRIVES
        } else {
            SearchMode.CURRENT_DRIVE
        }
        if (_uiState.value.searchMode != mode) {
            _uiState.update {
                it.copy(
                    searchMode = mode,
                    searchResults = emptyMap(),
                    currentDriveSearchResults = emptyList(),
                    tmdbSearchResults = emptyList()
                )
            }
        }
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(query)
            }
        }
    }

    private suspend fun searchCachedCurrentPath(
        query: String,
        driveId: String,
        folderId: String?
    ): List<MediaFileEntity> {
        if (folderId == null) {
            return driveRepository.searchFilesInDrive(query, driveId).first()
        }

        val allFiles = mediaFileDao.getFilesByDriveSync(driveId)
        val descendants = mutableListOf<MediaFileEntity>()
        val pendingFolders = ArrayDeque<String>()
        pendingFolders.add(folderId)

        while (pendingFolders.isNotEmpty()) {
            val currentFolderId = pendingFolders.removeFirst()
            val children = allFiles.filter { it.parentId == currentFolderId }
            descendants.addAll(children)
            children.filter { it.isFolder }.forEach { pendingFolders.add(it.id) }
        }

        return descendants
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<MediaFileEntity> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    private suspend fun searchTmdbCatalog(query: String): List<MediaFileEntity> {
        val metadataMatches = applyExactTmdbFilter(
            tmdbRepository.searchLocalCatalog(query),
            query
        )
            .distinctBy { it.driveFileId }
        if (metadataMatches.isEmpty()) return emptyList()

        val metadataByFileId = metadataMatches.associateBy { it.driveFileId }
        _uiState.update { it.copy(tmdbMetadata = it.tmdbMetadata + metadataByFileId) }

        val loadedCatalogFiles = (
            _uiState.value.homeSections.flatMap { it.items } +
                _uiState.value.homeRecentlyAdded
            ).distinctBy { it.id }

        val loadedMatches = loadedCatalogFiles.filter { it.id in metadataByFileId }
        val loadedIds = loadedMatches.map { it.id }.toSet()
        val configuredFolderIds = appPreferences.tmdbMovieFolders +
            appPreferences.tmdbTvFolders +
            appPreferences.tmdbRecentFolders
        val cachedMatches = if (configuredFolderIds.isEmpty()) {
            emptyList()
        } else {
            metadataMatches
                .filter { it.driveFileId !in loadedIds }
                .mapNotNull { mediaFileDao.getFileById(it.driveFileId) }
                .filter { it.parentId in configuredFolderIds }
        }
        val matches = (loadedMatches + cachedMatches).distinctBy { it.id }

        val orderByMetadata = metadataMatches
            .mapIndexed { index, metadata -> metadata.driveFileId to index }
            .toMap()
        return matches.sortedBy { orderByMetadata[it.id] ?: Int.MAX_VALUE }
    }

    private fun applyExactTmdbFilter(
        metadata: List<TmdbMetadataEntity>,
        query: String
    ): List<TmdbMetadataEntity> {
        if (!_uiState.value.isExactSearch) return metadata

        val queryTokens = query.normalizedSearchTokens()
        if (queryTokens.isEmpty()) return metadata

        return metadata.filter { item ->
            item.title.normalizedSearchTokens() == queryTokens
        }
    }

    private fun String.normalizedSearchTokens(): List<String> {
        return lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
    }

    fun getDriveName(driveId: String): String {
        if (driveId.isBlank()) return "Unknown Drive"
        if (driveId == "my_drive") return "My Drive"
        if (driveId == "shared_with_me") return "Shared with me"
        if (driveId == "starred") return "Starred"
        if (driveId == "trashed") return "Trashed"
        if (driveId == "recent") return "Recent"
        return _uiState.value.sharedDrives.find { it.id == driveId }?.name ?: driveId
    }

    fun refreshHomeContent(fromSwipe: Boolean = false) {
        // Show the full home skeleton for explicit user refreshes.
        // Keep tmdbMetadata so cached posters remain visible while new data loads.
        if (fromSwipe) {
            _uiState.update { it.copy(isHomeLoading = true, isHomeRefreshing = true) }
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                _uiState.update { it.copy(isHomeRefreshing = false) }
            }
        } else {
            _uiState.update { it.copy(isHomeLoading = true, isHomeRefreshing = false) }
        }
        // Clear folder timestamp cache to force API refresh
        lastLoadedTimestamps.clear()
        // Clear debounce so loadHomeContent always runs
        lastHomeLoadTimestamp = 0L
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
        val missingOriginalLanguageIds = directMetadata
            .filter { it.originalLanguage.isNullOrBlank() }
            .map { it.driveFileId }
            .toMutableSet()
        directMetadata.forEach { metadata ->
            metadataByFileId[metadata.driveFileId] = metadata
        }

        val missingItems = items.filter {
            it.fileId !in metadataByFileId || it.fileId in missingOriginalLanguageIds
        }
        if (missingItems.isNotEmpty()) {
            val filesById = missingItems.mapNotNull { item ->
                mediaFileDao.getFileById(item.fileId)?.let { file -> item.fileId to file }
            }.toMap()
            val parentIds = filesById.values.mapNotNull { it.parentId }.distinct()
            val parentMetadata = tmdbRepository.getMetadataForFiles(parentIds)
                .associateBy { it.driveFileId }

            filesById.forEach { (fileId, file) ->
                parentMetadata[file.parentId]?.let { metadata ->
                    if (fileId !in metadataByFileId || metadataByFileId[fileId]?.originalLanguage.isNullOrBlank()) {
                        metadataByFileId[fileId] = metadata
                    }
                    if (!metadata.originalLanguage.isNullOrBlank()) {
                        missingOriginalLanguageIds.remove(fileId)
                    }
                }
            }

            if (tmdbRepository.isConfigured()) {
                filesById.forEach { (fileId, file) ->
                    val needsMetadataFetch = fileId !in metadataByFileId || fileId in missingOriginalLanguageIds
                    if (needsMetadataFetch && continueMetadataFetchAttempted.add(fileId)) {
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



    override fun onCleared() {
        super.onCleared()
        appPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        offlineDebounceJob?.cancel()
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
                isMpvAvailable = appPreferences.isMpvAvailable(),
                hasTmdbSetup = appPreferences.tmdbApiKey.isNotEmpty(),
                tmdbConfiguredFolderIds = appPreferences.tmdbMovieFolders + appPreferences.tmdbTvFolders + appPreferences.tmdbRecentFolders,
                isGridView = appPreferences.isGridView
            )
        }
    }

    fun logout() {
        authRepository.logout()
    }

    private fun detectMetadataIdInFolder(folder: MediaFileEntity): String? {
        if (!folder.isFolder) return null

        val bracketed = Regex("""\[\s*(tt\d{5,}|\d+)\s*]""", RegexOption.IGNORE_CASE)
            .find(folder.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (bracketed != null) return bracketed

        val explicitTmdb = Regex("""\btmdb(?:\s*[-_ ]?\s*id)?\s*[-_:# ]\s*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(folder.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (explicitTmdb != null) return explicitTmdb

        val explicitImdb = Regex("""\bimdb(?:\s*[-_ ]?\s*id)?\s*[-_:# ]\s*(tt\d{5,})\b""", RegexOption.IGNORE_CASE)
            .find(folder.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (explicitImdb != null) return explicitImdb

        return Regex("""\btt\d{5,}\b""", RegexOption.IGNORE_CASE)
            .find(folder.name)
            ?.value
    }

    private companion object {
        private const val HOME_CONTENT_DEBOUNCE_MS = 60_000L
    }
}
