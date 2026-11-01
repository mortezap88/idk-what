package com.example.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.notepad.data.model.MediaItem
import com.example.notepad.data.model.MediaType
import com.example.notepad.ui.theme.MintGreenDot
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextOffWhite
import com.example.notepad.ui.theme.TranscribedSquircle
import com.example.notepad.ui.theme.TranscribedSquircleInk
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemSquircle(
    item: MediaItem,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp? = null
) {
    // A finished transcription flips the tile: dark on a light page and the other way round.
    val isTranscribed = item.isTranscribed && item.type == MediaType.AUDIO
    val hasError = !item.isProcessing && !item.isTranscribed && !item.transcriptionError.isNullOrBlank()
    val tileColor = if (isTranscribed) TranscribedSquircle else SquircleBg
    val tileBorder = when {
        isPlaying -> Color(0xFF7A58C6)
        hasError -> Color(0xFFE57373)
        isTranscribed -> TranscribedSquircle
        else -> SquircleBorder
    }
    val iconTint = if (isTranscribed) TranscribedSquircleInk else TextOffWhite

    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.aspectRatio(1f))
            .testTag("media_item_${item.id}")
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tileColor,
            border = BorderStroke(1.5.dp, tileBorder),
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
                if (item.isProcessing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MintGreenDot,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (item.type == MediaType.IMAGE) {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Image preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.5.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Image",
                            tint = TextOffWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // Audio item: play or pause triangle
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
