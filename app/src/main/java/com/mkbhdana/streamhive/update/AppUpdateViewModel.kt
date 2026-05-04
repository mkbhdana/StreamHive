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
    val availableUpdate: AppUpdateInfo? = null
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
        checkForUpdate()
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

    private companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
