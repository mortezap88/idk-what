package com.example.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.ChatMessage
import com.example.network.LinguisticCorrection
import com.example.network.TutorMessageFormatter
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationMessagePages(
    messages: List<ChatMessage>,
    maxFontSizeSp: Int,
    isBusy: Boolean,
    busyLabel: String,
    emptyLabel: String? = null,
    showInlineBusyIndicator: Boolean = true,
    modifier: Modifier = Modifier,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit = { _, _ -> },
    onRetryMessage: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = (messages.size - 1).coerceAtLeast(0),
        pageCount = { messages.size.coerceAtLeast(1) }
    )
    var activeCorrection by remember { mutableStateOf<Pair<String, LinguisticCorrection>?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val currentMessagesSize by rememberUpdatedState(messages.size)
    var isUserScrollActive by remember { mutableStateOf(false) }
    var manualPageChangeToken by remember { mutableIntStateOf(0) }
    var showPageCounter by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            isUserScrollActive = false
            pagerState.animateScrollToPage(messages.lastIndex)
        }
    }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow {
            Triple(pagerState.currentPage, pagerState.isScrollInProgress, isDragged)
        }.collect { (page, scrolling, dragged) ->
            if (dragged) {
                isUserScrollActive = true
            }
            if (page != previousPage) {
                previousPage = page
                if (isUserScrollActive && currentMessagesSize > 1) {
                    manualPageChangeToken++
                }
            }
            if (!scrolling && !dragged) {
                isUserScrollActive = false
            }
        }
    }

    LaunchedEffect(manualPageChangeToken) {
        if (manualPageChangeToken > 0) {
            showPageCounter = true
            delay(2000L)
            showPageCounter = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val message = messages[page]
                val answer = if (message.sender == "user") {
                    ""
                } else {
                    remember(message.text) {
                        TutorMessageFormatter.extractAnswerBody(message.text)
                    }
                }
                val hasVisibleAnswer = if (message.sender == "user") {
                    false
                } else {
                    remember(answer) {
                        TutorMessageFormatter.parseSegments(answer).isNotEmpty()
                    }
                }

                if (message.sender != "user" &&
                    message.isError &&
                    !hasVisibleAnswer &&
                    page == messages.lastIndex &&
                    onRetryMessage != null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = onRetryMessage) {
                            Text("Retry", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (message.sender == "user") {
                            Text(
                                text = "You",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                            val allImagesEncoded = remember(
                                message.id,
                                message.imageBase64,
                                message.imageBase64List
                            ) {
                                message.allImagesBase64()
                            }
                            val decodedBitmaps = remember(message.id, allImagesEncoded) {
                                allImagesEncoded.mapNotNull { encoded ->
                                    runCatching {
                                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            ?.asImageBitmap()
                                    }.getOrNull()
                                }
                            }
                            if (decodedBitmaps.size == 1) {
                                val singleBitmap = decodedBitmaps.first()
                                Image(
                                    bitmap = singleBitmap,
                                    contentDescription = "Attached image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { previewBitmap = singleBitmap }
                                )
                            } else if (decodedBitmaps.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    decodedBitmaps.forEachIndexed { idx, bmp ->
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Attached image ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(172.dp)
                                                .height(172.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { previewBitmap = bmp }
                                        )
                                    }
                                }
                            }
                            if (message.text.isNotEmpty()) {
                                ConversationCorrectedText(
                                    text = message.text,
                                    corrections = message.corrections,
                                    fontSizeSp = maxFontSizeSp,
                                    onCorrectionClick = { correction ->
                                        activeCorrection = message.id to correction
                                    }
                                )
                            } else if (!message.audioBase64.isNullOrBlank()) {
                                Text(
                                    text = "Voice message",
                                    color = TextMuted,
                                    fontSize = maxFontSizeSp.sp
                                )
                            } else if (decodedBitmaps.isEmpty()) {
                                ConversationCorrectedText(
                                    text = "",
                                    corrections = message.corrections,
                                    fontSizeSp = maxFontSizeSp,
                                    onCorrectionClick = { correction ->
                                        activeCorrection = message.id to correction
                                    }
                                )
                            }
                        } else {
                            if (hasVisibleAnswer) {
                                StructuredTutorMessage(
                                    rawText = answer,
                                    fontSizeSp = maxFontSizeSp
                                )
                            }
                            if (message.isError && page == messages.lastIndex && onRetryMessage != null) {
                                TextButton(
                                    onClick = onRetryMessage,
                                    modifier = Modifier.padding(top = if (hasVisibleAnswer) 8.dp else 0.dp)
                                ) {
                                    Text("Retry", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (showInlineBusyIndicator && isBusy && page == messages.lastIndex && !hasVisibleAnswer) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = TextMuted,
                                        strokeWidth = 2.dp
                                    )
                                    androidx.compose.foundation.layout.Spacer(
                                        modifier = Modifier.width(8.dp)
                                    )
                                    Text(
                                        text = busyLabel,
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = showPageCounter && messages.size > 1,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 450)),
                modifier = Modifier.align(BiasAlignment(horizontalBias = 0f, verticalBias = 0.42f))
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SurfaceCard.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.testTag("conversation_page_counter")
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${messages.size}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        } else if (isBusy && showInlineBusyIndicator) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = TextMuted,
                strokeWidth = 2.dp
            )
        } else if (emptyLabel != null) {
            Text(
                text = emptyLabel,
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    activeCorrection?.let { (messageId, correction) ->
        ConversationCorrectionDialog(
            correction = correction,
            onApply = {
                onApplyCorrection(messageId, correction.id)
                activeCorrection = null
            },
            onDismiss = { activeCorrection = null }
        )
    }

    previewBitmap?.let { bmp ->
        Dialog(
            onDismissRequest = { previewBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { previewBitmap = null }
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Full image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
                IconButton(
                    onClick = { previewBitmap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = Color.White
                    )
                }
            }
        }
    }
}



