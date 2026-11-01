package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.backup.FlashDataBackupManager
import com.example.ui.theme.AccentPink
import com.example.ui.theme.BadgeGreenBg
import com.example.ui.theme.BadgeGreenBorder
import com.example.ui.theme.BadgeGreenText
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@Composable
internal fun DataBackupSection(
    storedApiKeyCount: Int,
    reviewItemCount: Int,
    subjectCount: Int,
    backupState: BackupUiState,
    onExportFlashData: (Uri) -> Unit,
    onImportFlashData: (Uri) -> Unit
) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val isWorking = backupState is BackupUiState.Working

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            onExportFlashData(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val displayName = resolveDocumentDisplayName(context, uri)
            pendingImport = uri to displayName
        }
    }

    val backupSummary = "$storedApiKeyCount ${if (storedApiKeyCount == 1) "key" else "keys"} · " +
        "$reviewItemCount ${if (reviewItemCount == 1) "item" else "items"} · " +
        "$subjectCount ${if (subjectCount == 1) "subject" else "subjects"}"

    ExpandableSettingsCard(
        title = stringResource(R.string.settings_backup_title),
        subtitle = stringResource(R.string.settings_backup_subtitle),
        summaryBadge = backupSummary,
        icon = Icons.Default.Upload,
        initiallyExpanded = backupState !is BackupUiState.Idle,
        testTag = "opening_backup_section"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceInput)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Current app memory",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$storedApiKeyCount API ${if (storedApiKeyCount == 1) "key" else "keys"} · " +
                        "$reviewItemCount review ${if (reviewItemCount == 1) "item" else "items"} · " +
                        "$subjectCount ${if (subjectCount == 1) "subject" else "subjects"}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = ".flashdata",
                color = PrimaryPurple,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    exportLauncher.launch(FlashDataBackupManager.defaultExportFileName())
                },
                enabled = !isWorking,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_flashdata_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = stringResource(R.string.settings_backup_export_button),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_backup_export_button),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("*/*"))
                },
                enabled = !isWorking,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("import_flashdata_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.settings_backup_import_button),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_backup_import_button),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        when (backupState) {
            BackupUiState.Idle -> Unit
            is BackupUiState.Working -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceInput)
                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("backup_status_working"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryPurple
                    )
                    Text(
                        text = backupState.message,
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
            is BackupUiState.Success -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BadgeGreenBg)
                        .border(1.dp, BadgeGreenBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("backup_status_success"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = backupState.message,
                        color = BadgeGreenText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is BackupUiState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentPink.copy(alpha = 0.12f))
                        .border(1.dp, AccentPink.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("backup_status_error"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = backupState.message,
                        color = AccentPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_backup_encryption_note),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    pendingImport?.let { (uri, fileName) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.settings_backup_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_backup_confirm_body, fileName),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImport = null
                        onImportFlashData(uri)
                    },
                    modifier = Modifier.testTag("confirm_import_flashdata_button")
                ) {
                    Text(stringResource(R.string.settings_backup_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingImport = null },
                    modifier = Modifier.testTag("cancel_import_flashdata_button")
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_cancel_action),
                        color = TextSecondary
                    )
                }
            }
        )
    }
}

private fun resolveDocumentDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: ".flashdata file"
}
