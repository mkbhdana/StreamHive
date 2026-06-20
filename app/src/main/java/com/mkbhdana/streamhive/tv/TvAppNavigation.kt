package com.mkbhdana.streamhive.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.mkbhdana.streamhive.data.model.AuthState
import com.mkbhdana.streamhive.tv.auth.TvAuthViewModel
import com.mkbhdana.streamhive.tv.auth.TvQrLoginScreen

/**
 * Root of the Android TV experience. Mirrors the mobile auth gate, but uses the
 * QR / second-screen login. When the shared auth state becomes Authenticated the
 * TV advances to [TvMainScreen]; logging out flips it back to the QR screen.
 */
@UnstableApi
@Composable
fun TvAppNavigation() {
    val authViewModel: TvAuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    if (authState is AuthState.Authenticated) {
        TvMainScreen(onLoggedOut = { /* authState flips to Unauthenticated → recomposes to login */ })
    } else {
        TvQrLoginScreen(onAuthenticated = { /* authState drives recomposition */ }, viewModel = authViewModel)
    }
}
