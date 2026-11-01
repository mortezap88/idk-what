package com.example.notepad.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notepad.ui.theme.CyanAccent
import com.example.notepad.ui.theme.DarkNotepadColors
import com.example.notepad.ui.theme.LightNotepadColors
import com.example.notepad.ui.theme.LocalNotepadColors
import com.example.notepad.ui.theme.NotepadCardBg
import com.example.notepad.ui.theme.NotepadCardBorder
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextOffWhite
import com.example.ui.theme.LocalIsDarkTheme
import java.util.concurrent.Executors

@Composable
fun CameraCaptureDialog(
    onPhotoCaptured: (Bitmap) -> Unit,
    onGalleryPick: (android.net.Uri) -> Unit = {},
    onGalleryPickMultiple: ((List<android.net.Uri>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val isDark = LocalIsDarkTheme.current
    val notepadColors = if (isDark) DarkNotepadColors else LightNotepadColors

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onGalleryPick(uri)
        }
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onGalleryPickMultiple?.invoke(uris)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalNotepadColors provides notepadColors) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NotepadCardBg,
                border = BorderStroke(1.5.dp, NotepadCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("camera_photo_capture_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Just the viewfinder.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCapture = capture

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("CameraCaptureDialog", "Camera init failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Keep one small way out, tucked into the corner.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .clickable(onClick = onDismiss)
                                .testTag("close_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shutter, with the gallery picker next to it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(68.dp)
                                .border(3.dp, CyanAccent, CircleShape)
                                .clickable {
                                    val capture = imageCapture
                                    if (capture != null) {
                                        capture.takePicture(
                                            cameraExecutor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val bitmap = imageProxyToBitmap(image)
                                                    image.close()
                                                    ContextCompat.getMainExecutor(context).execute {
                                                        if (bitmap != null) {
                                                            onPhotoCaptured(bitmap)
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e(
                                                        "CameraCaptureDialog",
                                                        "Photo capture failed",
                                                        exception
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                .testTag("camera_shutter_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(CyanAccent, CircleShape)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SquircleBg,
                            border = BorderStroke(1.dp, SquircleBorder),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable {
                                    val request = PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                    if (onGalleryPickMultiple != null) {
                                        multiplePhotoPickerLauncher.launch(request)
                                    } else {
                                        singlePhotoPickerLauncher.launch(request)
                                    }
                                }
                                .testTag("camera_gallery_picker_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Pick from gallery",
                                    tint = TextOffWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0 && bitmap != null) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e("CameraCaptureDialog", "Failed to convert ImageProxy to Bitmap", e)
        null
    }
}
