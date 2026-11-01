package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.Flashcard
import com.example.data.FlashcardTypes
import com.example.data.SpacedRepetitionUtils
import com.example.data.SrsStage
import com.example.network.ChatMessage
import com.example.network.TutorMessageFormatter
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.BgDark
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.network.AvalAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(
    cards: List<Flashcard>,
    currentIndex: Int,
    maxFontSizeSp: Int,
    scenarioLoading: Boolean,
    scenarioOrQuestion: String?,
    reviewChatHistory: List<ChatMessage>,
    isReviewReplying: Boolean,
    onSendReviewReply: (String) -> Unit,
    onSendReviewAudioReply: (String) -> Unit,
    onRetryReviewReply: () -> Unit,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit,
    onSelectStage: (Flashcard, Int) -> Unit,
    onMarkDumb: (Flashcard) -> Unit,
    onMarkLearned: (Flashcard) -> Unit,
    onOpenExtractQuestion: () -> Unit = {},
    onSpeak: (String) -> Unit,
    onClose: () -> Unit
) {
    val currentCard = cards.getOrNull(currentIndex)?.takeIf { SpacedRepetitionUtils.isCardDue(it) }
    var isKeyboardVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentCard?.id) {
        isKeyboardVisible = false
    }

    BackHandler { onClose() }
    BackHandler(enabled = isKeyboardVisible) {
        isKeyboardVisible = false
    }

    KeyboardDismissSurface(
        isKeyboardVisible = isKeyboardVisible,
        onDismissKeyboard = { isKeyboardVisible = false },
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = BgDark
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (currentCard != null) {
                    TopStageReviewBar(
                        currentStageIndex = currentCard.stageIndex,
                        onSelectStage = { stageIndex ->
                            onSelectStage(currentCard, stageIndex)
                        }
                    )
                }

                if (currentCard == null) {
                    // Completed session view
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎉",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "All Done for Now!",
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You've completed your scheduled reviews.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Return to Stages", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (currentCard.type) {
                            FlashcardTypes.CONVERSATION_LINGUISTIC,
                            FlashcardTypes.CONVERSATION_TECHNICAL,
                            FlashcardTypes.EXAM -> {
                                ConversationReviewContent(
                                    isLoading = scenarioLoading,
                                    maxFontSizeSp = maxFontSizeSp,
                                    chatHistory = reviewChatHistory,
                                    isReplying = isReviewReplying,
                                    isKeyboardVisible = isKeyboardVisible,
                                    onKeyboardVisibleChange = { isKeyboardVisible = it },
                                    onSendReply = onSendReviewReply,
                                    onSendAudioReply = onSendReviewAudioReply,
                                    onRetryReply = onRetryReviewReply,
                                    onApplyCorrection = onApplyCorrection
                                )
                            }

                            FlashcardTypes.TEXT -> {
                                TextReviewContent(card = currentCard)
                            }

                            FlashcardTypes.SPELLING -> {
                                SpellingReviewContent(
                                    card = currentCard,
                                    onSpeak = { onSpeak(currentCard.word) }
                                )
                            }

                            FlashcardTypes.PRONUNCIATION -> {
                                PronunciationReviewContent(
                                    card = currentCard,
                                    onSpeak = { onSpeak(currentCard.word) }
                                )
                            }

                            FlashcardTypes.IMAGE -> {
                                ImageReviewContent(
                                    card = currentCard,
                                    onSpeak = { onSpeak(currentCard.word) }
                                )
                            }
                        }
                    }

                    if (!isKeyboardVisible) {
                        BottomReviewArchiveBar(
                            currentStageIndex = currentCard.stageIndex,
                            onMarkDumb = { onMarkDumb(currentCard) },
                            onMarkLearned = { onMarkLearned(currentCard) },
                            onExtractQuestion = onOpenExtractQuestion
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopStageReviewBar(
    currentStageIndex: Int,
    onSelectStage: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SrsStage.entries.forEachIndexed { idx, stage ->
            val isCurrent = idx == currentStageIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isCurrent) PrimaryPurple else SurfaceInput)
                    .border(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) PrimaryPurpleLight else SurfaceCardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable(role = Role.Button) { onSelectStage(idx) }
                    .padding(horizontal = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stage.shortName,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else TextMuted,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BottomReviewArchiveBar(
    currentStageIndex: Int,
    onMarkDumb: () -> Unit,
    onMarkLearned: () -> Unit,
    onExtractQuestion: () -> Unit
) {
    val isReadyToGraduate = currentStageIndex >= SrsStage.ONE_MONTH.ordinal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onMarkDumb)
                .testTag("review_dumb_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceInput)
                    .border(
                        width = 1.dp,
                        color = SurfaceCardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dumb",
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onMarkLearned)
                .testTag("review_learned_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceInput)
                    .border(
                        width = if (isReadyToGraduate) 1.5.dp else 1.dp,
                        color = if (isReadyToGraduate) PrimaryPurpleLight else SurfaceCardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Learned",
                    color = if (isReadyToGraduate) PrimaryPurpleLight else TextMuted,
                    fontWeight = if (isReadyToGraduate) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onExtractQuestion)
                .testTag("review_extract_question_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceInput)
                    .border(
                        width = 1.dp,
                        color = SurfaceCardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.review_extract_question_button),
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ConversationReviewContent(
    isLoading: Boolean,
    maxFontSizeSp: Int,
    chatHistory: List<ChatMessage>,
    isReplying: Boolean,
    isKeyboardVisible: Boolean,
    onKeyboardVisibleChange: (Boolean) -> Unit,
    onSendReply: (String) -> Unit,
    onSendAudioReply: (String) -> Unit,
    onRetryReply: () -> Unit,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit
) {
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    var isMicrophoneHeld by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isFinalizingVoice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()
    val audioRecorder = remember(context) { com.example.audio.AudioRecorderManager(context) }
    val liveAudioLevel by audioRecorder.audioLevel.collectAsState()
    val currentOnSendReply by rememberUpdatedState(onSendReply)
    val currentOnSendAudioReply by rememberUpdatedState(onSendAudioReply)
    val currentIsKeyboardVisible by rememberUpdatedState(isKeyboardVisible)
    val currentOnKeyboardVisibleChange by rememberUpdatedState(onKeyboardVisibleChange)

    val hasVisibleStreamingReply = remember(chatHistory, isReplying) {
        if (!isReplying) {
            false
        } else {
            val tail = chatHistory.lastOrNull()
            tail != null && tail.sender != "user" &&
                TutorMessageFormatter.parseSegments(
                    TutorMessageFormatter.extractAnswerBody(tail.text)
                ).isNotEmpty()
        }
    }

    val activityPhase = when {
        isRecording -> ReviewActivityPhase.LISTENING
        isFinalizingVoice || isLoading || (isReplying && !hasVisibleStreamingReply) ->
            ReviewActivityPhase.THINKING
        else -> ReviewActivityPhase.IDLE
    }

    RecordingVibration(isRecording = isRecording)

    DisposableEffect(audioRecorder) {
        onDispose { audioRecorder.stopRecording() }
    }

    fun sendReply(text: String = replyText.text) {
        if (text.isBlank() || isReplying || isFinalizingVoice) return
        currentOnSendReply(text.trim())
        replyText = TextFieldValue("")
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && isMicrophoneHeld) {
            if (audioRecorder.startRecording()) {
                isRecording = true
                AvalAiClient.warmUpConnectionAsync()
            } else {
                isMicrophoneHeld = false
                Toast.makeText(
                    context,
                    "Could not access the microphone",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!isGranted) {
            isMicrophoneHeld = false
            Toast.makeText(
                context,
                "Microphone permission is required to record voice notes",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ConversationMessagePages(
                    messages = chatHistory,
                    maxFontSizeSp = maxFontSizeSp,
                    isBusy = isLoading || isReplying,
                    busyLabel = if (isLoading) "Preparing your review..." else "Thinking...",
                    emptyLabel = null,
                    showInlineBusyIndicator = false,
                    onApplyCorrection = onApplyCorrection,
                    onRetryMessage = onRetryReply,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isReplying, isFinalizingVoice, context) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pointerId = down.id
                                var wasConsumed = down.isConsumed
                                var isDragGesture = false
                                var recordingRequested = false

                                val holdJob = gestureScope.launch {
                                    delay(280L)
                                    if (!isDragGesture && !wasConsumed && !isReplying && !isFinalizingVoice) {
                                        recordingRequested = true
                                        isMicrophoneHeld = true
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else if (audioRecorder.startRecording()) {
                                            isRecording = true
                                            AvalAiClient.warmUpConnectionAsync()
                                        } else {
                                            isMicrophoneHeld = false
                                            Toast.makeText(
                                                context,
                                                "Could not access the microphone",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: break
                                    wasConsumed = wasConsumed || change.isConsumed
                                    if ((change.position - down.position).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        isDragGesture = true
                                        holdJob.cancel()
                                    }
                                    if (!change.pressed) break
                                }
                                holdJob.cancel()

                                if (recordingRequested) {
                                    isMicrophoneHeld = false
                                    if (isRecording) {
                                        isRecording = false
                                        isFinalizingVoice = true
                                        gestureScope.launch {
                                            try {
                                                val audioBase64 = withContext(Dispatchers.Default) {
                                                    audioRecorder.stopRecording()
                                                }
                                                if (!audioBase64.isNullOrBlank()) {
                                                    currentOnSendAudioReply(audioBase64)
                                                }
                                            } finally {
                                                isFinalizingVoice = false
                                            }
                                        }
                                    }
                                } else if (!isDragGesture) {
                                    if (currentIsKeyboardVisible) {
                                        currentOnKeyboardVisibleChange(false)
                                    } else if (!wasConsumed && !isReplying && !isFinalizingVoice) {
                                        currentOnKeyboardVisibleChange(true)
                                    }
                                }
                            }
                        }
                )

                ReviewActivityIndicator(
                    phase = activityPhase,
                    audioLevel = liveAudioLevel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = if (isKeyboardVisible) 84.dp else 14.dp
                        )
                )
            }
        }

        CreationVirtualKeyboard(
            visible = isKeyboardVisible,
            text = replyText,
            onTextChange = { replyText = it },
            onSend = { sendReply(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun TextReviewContent(card: Flashcard) {
    var isRevealed by remember(card.id) { mutableStateOf(false) }
    val backText = card.backText?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Text Card",
            color = PrimaryPurpleLight,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            BidiText(
                text = card.word,
                color = TextPrimary,
                fontSize = 20.sp,
                lineHeight = 30.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isRevealed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                BidiText(
                    text = backText ?: "No back text for this card.",
                    color = if (backText == null) TextMuted else TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 27.sp
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Button(
                onClick = { isRevealed = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.7f)
            ) {
                Text("Reveal Back", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SpellingReviewContent(
    card: Flashcard,
    onSpeak: () -> Unit
) {
    var isRevealed by remember(card.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Spelling Review",
            color = PrimaryPurpleLight,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Speaker icon to play audio
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SurfaceCard)
                .border(2.dp, PrimaryPurple, CircleShape)
                .clickable { onSpeak() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Play Audio",
                tint = PrimaryPurpleLight,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Listen to the word and spell it in your mind.",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isRevealed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceInput)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = card.word,
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Button(
                onClick = { isRevealed = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.7f)
            ) {
                Text("Reveal Spelling", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun PronunciationReviewContent(
    card: Flashcard,
    onSpeak: () -> Unit
) {
    var isRevealed by remember(card.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pronunciation Review",
            color = AccentTeal,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 36.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = card.word,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Say the word aloud or in your mind.",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isRevealed) {
            Button(
                onClick = onSpeak,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hear Native Pronunciation", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Button(
                onClick = {
                    isRevealed = true
                    onSpeak()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.7f)
            ) {
                Text("Reveal & Check", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ImageReviewContent(
    card: Flashcard,
    onSpeak: () -> Unit
) {
    var isRevealed by remember(card.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Image Recognition",
            color = PrimaryPurpleLight,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceInput)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!card.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = "Flashcard Target Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("No Image Available", color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What is the word or term for this image?",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isRevealed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = card.word,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onSpeak) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen", tint = PrimaryPurpleLight)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Button(
                onClick = { isRevealed = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.7f)
            ) {
                Text("Reveal Word", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
