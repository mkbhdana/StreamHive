package com.mkbhdana.streamhive.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.catalog.FolderInfo

@Composable
fun FolderBreadcrumb(
    driveName: String,
    folderStack: List<FolderInfo>,
    onNavigateToRoot: () -> Unit,
    onNavigateToIndex: (Int) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to end when stack changes
    LaunchedEffect(folderStack.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home / root
            BreadcrumbChip(
                label = driveName,
                isActive = folderStack.isEmpty(),
                isLoading = isLoading && folderStack.isEmpty(),
                onClick = {
                    if (!isLoading) onNavigateToRoot()
                },
                showHomeIcon = true
            )

            // Folder stack
            folderStack.forEachIndexed { index, folder ->
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                BreadcrumbChip(
                    label = folder.name,
                    isActive = index == folderStack.lastIndex,
                    isLoading = isLoading && index == folderStack.lastIndex,
                    onClick = {
                        if (!isLoading && index < folderStack.lastIndex) {
                            onNavigateToIndex(index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbChip(
    label: String,
    isActive: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    showHomeIcon: Boolean = false
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        label = "breadcrumb_bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "breadcrumb_text"
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.clickable(enabled = !isLoading, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showHomeIcon) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            if (isLoading) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = textColor
                )
            }
        }
    }
}
