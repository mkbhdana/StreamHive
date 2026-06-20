package com.mkbhdana.streamhive.tv.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.data.model.AuthState
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor as TextSecondary
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor as TvBackground

/**
 * Sign-in screen for the TV: shows a QR code pointing to the locally-served
 * login page. The user scans it with a phone, signs in there, and this screen
 * advances automatically when the shared auth state becomes Authenticated.
 */
@Composable
fun TvQrLoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: TvAuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startServer() }
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) onAuthenticated()
    }

    val qr = remember(viewModel.loginUrl) {
        viewModel.loginUrl?.let { QrCode.generate(it, 600) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(TvDimens.Overscan),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp)
        ) {
            // QR panel
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qr != null -> Image(
                        bitmap = qr,
                        contentDescription = "Sign-in QR code",
                        modifier = Modifier.size(280.dp)
                    )
                    viewModel.serverError != null -> Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(120.dp)
                    )
                    else -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Instructions
            Column(modifier = Modifier.width(420.dp)) {
                Text(
                    "Sign in to StreamHive",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                viewModel.serverError?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } ?: run {
                    InstructionLine("1.  Scan the QR code with your phone's camera.")
                    InstructionLine("2.  Sign in on the web page that opens (OAuth or Service Account).")
                    InstructionLine("3.  This screen continues automatically once you're signed in.")
                    Spacer(Modifier.height(20.dp))
                    viewModel.loginUrl?.let {
                        Text(
                            "Or open on your phone browser:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "Phone and TV must be on the same network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                if (authState is AuthState.Loading) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Signing in...", color = TextSecondary)
                    }
                } else if (authState is AuthState.Error) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        (authState as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}
