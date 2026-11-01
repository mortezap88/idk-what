package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.SubjectImageAttachment
import com.example.notepad.ui.components.CameraCaptureDialog
import com.example.notepad.ui.theme.DeleteButtonBg
import com.example.notepad.ui.theme.DeleteButtonBorder
import com.example.notepad.ui.theme.DeleteButtonText
import com.example.notepad.ui.theme.MintGreenDot
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextOffWhite
import com.example.ui.theme.EvalGreenDot
import com.example.ui.theme.EvalRedBorder
import com.example.ui.theme.EvalRedDot
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

private const val SUBJECT_IMAGES_PER_ROW = 6

@Composable
fun SubjectMaterialImagesSection(
    images: List<SubjectImageAttachment>,
    onAddCameraImage: (Bitmap) -> Unit,
    onAddGalleryImages: (List<Uri>) -> Unit,
    onDeleteImage: (String) -> Unit,
    onRetryOcr: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCameraDialog by remember { mutableStateOf(false) }
    var previewImageId by remember { mutableStateOf<String?>(null) }
    var deletePopupImageId by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraDialog = true
        } else {
            Toast.makeText(
                context,
                "Camera permission needed to capture photos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openAddImageDialog() {
        onDismissKeyboard()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showCameraDialog = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val processingCount = images.count { it.isProcessingOcr }
    val failedCount = images.count { it.ocrFailed && !it.isProcessingOcr }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("subject_material_images_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    images.isEmpty() -> "Images (DeepSeek OCR)"
                    else -> "Images (${images.size})"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    processingCount > 0 -> "Extracting OCR ($processingCount)…"
                    failedCount > 0 -> "$failedCount failed · tap to retry"
                    images.isNotEmpty() -> "OCR ready · tap box to inspect"
                    else -> "Camera or multi-select gallery"
                },
                color = when {
                    processingCount > 0 -> PrimaryPurpleLight
                    failedCount > 0 -> EvalRedDot
                    images.isNotEmpty() -> EvalGreenDot
                    else -> TextMuted
                },
                fontSize = 11.sp
            )
        }

        val totalSlots = images.size + 1
        val rowCount = (totalSlots + SUBJECT_IMAGES_PER_ROW - 1) / SUBJECT_IMAGES_PER_ROW
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (rowIndex in 0 until rowCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (colIndex in 0 until SUBJECT_IMAGES_PER_ROW) {
                        val slotIndex = rowIndex * SUBJECT_IMAGES_PER_ROW + colIndex
                        when {
                            slotIndex < images.size -> {
                                val item = images[slotIndex]
                                key(item.id) {
                                    SubjectImageSquircle(
                                        item = item,
                                        onTap = {
                                            onDismissKeyboard()
                                            previewImageId = item.id
                                        },
                                        onLongPress = {
                                            onDismissKeyboard()
                                            deletePopupImageId = item.id
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            slotIndex == images.size -> {
                                key("add_subject_image_squircle") {
                                    AddSubjectImageSquircle(
                                        onClick = { openAddImageDialog() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            else -> {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCameraDialog) {
        CameraCaptureDialog(
            onPhotoCaptured = { bitmap ->
                showCameraDialog = false
                onAddCameraImage(bitmap)
            },
            onGalleryPick = { uri ->
                showCameraDialog = false
                onAddGalleryImages(listOf(uri))
            },
            onGalleryPickMultiple = { uris ->
                showCameraDialog = false
                onAddGalleryImages(uris)
            },
            onDismiss = {
                showCameraDialog = false
            }
        )
    }

    val previewItem = previewImageId?.let { id -> images.firstOrNull { it.id == id } }
    if (previewItem != null) {
        SubjectImagePreviewDialog(
            item = previewItem,
            onRetryOcr = { onRetryOcr(previewItem.id) },
            onDelete = {
                previewImageId = null
                onDeleteImage(previewItem.id)
            },
            onDismiss = { previewImageId = null }
        )
    }

    val deleteItem = deletePopupImageId?.let { id -> images.firstOrNull { it.id == id } }
    if (deleteItem != null) {
        SubjectImageDeletePopup(
            onConfirmDelete = {
                val id = deleteItem.id
                deletePopupImageId = null
                onDeleteImage(id)
            },
            onDismiss = { deletePopupImageId = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubjectImageSquircle(
    item: SubjectImageAttachment,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        item.ocrFailed && !item.isProcessingOcr -> EvalRedBorder
        else -> SquircleBorder
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("subject_image_box_${item.id}")
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SquircleBg,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val file = File(item.filePath)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Attached material image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.5.dp))
                    )
                }

                if (item.isProcessingOcr) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MintGreenDot,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (item.ocrText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EvalGreenDot)
                    )
                } else if (item.ocrFailed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EvalRedDot)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSubjectImageSquircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("subject_material_add_image_box")
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SquircleBg,
            border = BorderStroke(1.5.dp, SquircleBorder),
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add image from camera or gallery",
                    tint = TextOffWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SubjectImagePreviewDialog(
    item: SubjectImageAttachment,
    onRetryOcr: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.84f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.5.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .testTag("subject_image_preview_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attached Page",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRetryOcr,
                                enabled = !item.isProcessingOcr,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-run OCR",
                                    tint = PrimaryPurpleLight
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("subject_delete_image_preview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete image",
                                    tint = EvalRedDot
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close preview",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }

                    val file = File(item.filePath)
                    AsyncImage(
                        model = file,
                        contentDescription = "Full image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp, max = 160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceInput)
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            item.isProcessingOcr -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = PrimaryPurpleLight
                                    )
                                    Text(
                                        text = "Extracting text via DeepSeek OCR…",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            item.ocrText.isNotBlank() -> {
                                Text(
                                    text = "Extracted OCR text",
                                    color = EvalGreenDot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                BidiText(
                                    text = item.ocrText,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }

                            item.ocrFailed -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "OCR could not extract text right now.",
                                        color = EvalRedDot,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onRetryOcr) {
                                        Text("Retry OCR", color = PrimaryPurpleLight, fontSize = 12.sp)
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = "No readable text detected on this image.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectImageDeletePopup(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeleteButtonBg,
                border = BorderStroke(1.5.dp, DeleteButtonBorder),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(140.dp)
                    .height(64.dp)
                    .clickable { onConfirmDelete() }
                    .testTag("subject_delete_image_confirm_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = DeleteButtonText,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Delete",
                        color = DeleteButtonText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
