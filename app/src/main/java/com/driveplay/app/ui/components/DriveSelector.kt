package com.driveplay.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveplay.app.catalog.DriveSection
import com.driveplay.app.data.model.SharedDrive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSelector(
    drives: List<SharedDrive>,
    selectedDrive: SharedDrive?,
    onDriveSelected: (SharedDrive) -> Unit,
    modifier: Modifier = Modifier,
    // New section-based API (backward compatible)
    sections: List<DriveSection> = emptyList(),
    selectedSection: DriveSection? = null,
    onSectionSelected: (DriveSection) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    val displayLabel = selectedSection?.label ?: selectedDrive?.name ?: "Select Drive"
    val displayIcon = selectedSection?.icon ?: Icons.Default.CloudQueue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                Icon(
                    displayIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (sections.isNotEmpty()) {
                // ── Built-in sections ──
                val builtIn = sections.filterNot { it is DriveSection.SharedDriveSection }
                val sharedDriveSections = sections.filterIsInstance<DriveSection.SharedDriveSection>()

                builtIn.forEach { section ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    section.icon, null,
                                    tint = if (section == selectedSection)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    section.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (section == selectedSection) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            onSectionSelected(section)
                            expanded = false
                        }
                    )
                }

                if (sharedDriveSections.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // Section header
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Shared Drives",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {},
                        enabled = false
                    )

                    sharedDriveSections.forEach { section ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudQueue, null,
                                        tint = if (section == selectedSection)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        section.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (section == selectedSection) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                onSectionSelected(section)
                                expanded = false
                            }
                        )
                    }
                }
            } else {
                // Fallback: old SharedDrive-only mode
                drives.forEach { drive ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    tint = if (drive == selectedDrive)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(drive.name, color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        onClick = {
                            onDriveSelected(drive)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
