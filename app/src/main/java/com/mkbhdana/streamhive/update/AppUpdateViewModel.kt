package com.mkbhdana.streamhive.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.settings.AppPreferences
import com.mkbhdana.streamhive.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateUiState(
    val availableUpdate: AppUpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val updateStatusMessage: String? = null
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val appUpdateRepository: AppUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        // Check once at app start (ViewModel creation). No continuous polling — the
        // update prompt only appears on a fresh launch/restart.
        checkForUpdate(force = true)
    }

    fun checkForUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val checkedRecently = now - appPreferences.lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS
        if (!force && checkedRecently) return
        if (!NetworkUtils.isNetworkAvailable(context)) return

        viewModelScope.launch {
            kotlinx.coroutines.delay(2_500)
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
                        // App startup must never depend on GitHub availability.
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

    fun downloadAndInstallUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        if (_uiState.value.isDownloadingUpdate) return

        if (!appUpdateRepository.canRequestPackageInstalls()) {
            appUpdateRepository.openInstallPermissionSettings()
            _uiState.update {
                it.copy(
                    updateStatusMessage = "Allow StreamHive to install unknown apps, then tap Download again."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0,
                updateStatusMessage = null
            )
        }

        viewModelScope.launch {
            appUpdateRepository.downloadUpdateApk(update) { progress ->
                _uiState.update { it.copy(updateDownloadProgress = progress) }
            }.fold(
                onSuccess = { apkFile ->
                    runCatching { appUpdateRepository.launchApkInstaller(apkFile) }
                        .fold(
                            onSuccess = {
                                _uiState.update {
                                    it.copy(
                                        availableUpdate = null,
                                        isDownloadingUpdate = false,
                                        updateDownloadProgress = 100,
                                        updateStatusMessage = "Opening installer"
                                    )
                                }
                            },
                            onFailure = { error ->
                                _uiState.update {
                                    it.copy(
                                        isDownloadingUpdate = false,
                                        updateStatusMessage = error.message ?: "Unable to open installer"
                                    )
                                }
                            }
                        )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateStatusMessage = error.message ?: "Update download failed"
                        )
                    }
                }
            )
        }
    }

    fun clearUpdateStatusMessage() {
        _uiState.update { it.copy(updateStatusMessage = null) }
    }

    private companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    }
}
