package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Flashcard
import com.example.data.FlashcardTypes
import com.example.data.SpacedRepetitionUtils
import com.example.data.Subject
import com.example.data.SubjectImageAttachment
import com.example.network.ChatMessage
import com.example.ui.theme.AccentPink
import com.example.ui.theme.BadgeGreenBg
import com.example.ui.theme.BadgeGreenBorder
import com.example.ui.theme.BadgeGreenText
import com.example.ui.theme.BgDark
import com.example.ui.theme.EvalBlueBg
import com.example.ui.theme.EvalBlueBorder
import com.example.ui.theme.EvalBlueDot
import com.example.ui.theme.EvalGreenBg
import com.example.ui.theme.EvalGreenBorder
import com.example.ui.theme.EvalGreenDot
import com.example.ui.theme.EvalRedBg
import com.example.ui.theme.EvalRedBorder
import com.example.ui.theme.EvalRedDot
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class SubjectSheetMode {
    NONE,
    ADD_QUESTION_CONVERSATION,
    TEXTBOOK,
    EXTRA_KNOWLEDGE
}

@Composable
fun SubjectCentreScreen(
    subjects: List<Subject>,
    cards: List<Flashcard>,
    maxFontSizeSp: Int,
    generatingSubjectIds: Set<Long>,
    generatingSubjectProgress: Map<Long, SubjectGenerationProgress> = emptyMap(),
    subjectStatusMessages: Map<Long, SubjectStatusNotice>,
    subjectQuestionMessages: List<ChatMessage>,
    isSubjectQuestionThinking: Boolean,
    subjectQuestionDraftState: SubjectQuestionDraftState = SubjectQuestionDraftState.Idle,
    onCreateSubject: (name: String) -> Boolean,
    onUpdateTextbook: (subjectId: Long, textbook: String) -> Unit,
    onUpdateExtraKnowledge: (subjectId: Long, extraKnowledge: String) -> Unit,
    onAddMaterialCameraImage: (subjectId: Long, kind: SubjectMaterialKind, bitmap: Bitmap) -> Unit = { _, _, _ -> },
    onAddMaterialGalleryImages: (subjectId: Long, kind: SubjectMaterialKind, uris: List<Uri>) -> Unit = { _, _, _ -> },
    onDeleteMaterialImage: (subjectId: Long, kind: SubjectMaterialKind, imageId: String) -> Unit = { _, _, _ -> },
    onRetryMaterialImageOcr: (subjectId: Long, kind: SubjectMaterialKind, imageId: String) -> Unit = { _, _, _ -> },
    onGenerateQuestions: (subjectId: Long) -> Unit,
    onCopyQuestionBank: (subjectId: Long) -> Unit = {},
    onDeleteQuestionBank: (subjectId: Long) -> Unit = {},
    onDismissSubjectStatus: (subjectId: Long) -> Unit,
    onSendSubjectQuestionMessage: (subjectId: Long, text: String, imageBitmaps: List<Bitmap>) -> Unit,
    onSendSubjectQuestionAudio: (subjectId: Long, audioBase64: String, imageBitmaps: List<Bitmap>) -> Unit,
    onApplySubjectQuestionCorrection: (messageId: String, correctionId: String) -> Unit,
    onClearSubjectQuestionChat: () -> Unit,
    onGenerateDraftQuestionFromConversation: (subjectId: Long) -> Unit,
    onDismissSubjectQuestionDraft: () -> Unit = {},
    onSaveGeneratedSubjectQuestion: (subjectId: Long) -> Boolean,
    onDeleteSubject: (Subject) -> Unit,
    onDeleteQuestion: (Flashcard) -> Unit,
    onRestoreQuestion: (Flashcard) -> Unit = {},
    onStartReview: (subjectId: Long) -> Unit
) {
    var openSubjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var activeSheetMode by rememberSaveable { mutableStateOf(SubjectSheetMode.NONE) }
    var subjectPendingDelete by remember { mutableStateOf<Subject?>(null) }

    val openSubject = subjects.firstOrNull { it.id == openSubjectId }

    Box(modifier = Modifier.fillMaxSize()) {
        if (openSubject == null) {
            SubjectListContent(
                subjects = subjects,
                cards = cards,
                onCreate = { showCreateSheet = true },
                onOpen = {
                    activeSheetMode = SubjectSheetMode.NONE
                    openSubjectId = it.id
                },
                onDelete = { subjectPendingDelete = it }
            )
        } else {
            BackHandler(enabled = activeSheetMode == SubjectSheetMode.NONE) {
                openSubjectId = null
            }

            SubjectDetailContent(
                subject = openSubject,
                cards = cards.filter { it.subjectId == openSubject.id },
                isGeneratingQuestions = openSubject.id in generatingSubjectIds,
                generationProgress = generatingSubjectProgress[openSubject.id],
                statusNotice = subjectStatusMessages[openSubject.id],
                onBack = { openSubjectId = null },
                onStartReview = { onStartReview(openSubject.id) },
                onGenerateQuestions = { onGenerateQuestions(openSubject.id) },
                onCopyQuestionBank = { onCopyQuestionBank(openSubject.id) },
                onDeleteQuestionBank = { onDeleteQuestionBank(openSubject.id) },
                onOpenAddQuestion = {
                    onClearSubjectQuestionChat()
                    activeSheetMode = SubjectSheetMode.ADD_QUESTION_CONVERSATION
                },
                onOpenTextbook = { activeSheetMode = SubjectSheetMode.TEXTBOOK },
                onOpenExtraKnowledge = { activeSheetMode = SubjectSheetMode.EXTRA_KNOWLEDGE },
                onDismissStatus = { onDismissSubjectStatus(openSubject.id) },
                onRestoreQuestion = onRestoreQuestion,
                onDeleteQuestion = onDeleteQuestion
            )
        }

        if (showCreateSheet) {
            CreateSubjectSheet(
                onDismiss = { showCreateSheet = false },
                onSave = { name ->
                    if (onCreateSubject(name)) {
                        showCreateSheet = false
                    }
                }
            )
        }

        if (openSubject != null) {
            when (activeSheetMode) {
                SubjectSheetMode.NONE -> Unit

                SubjectSheetMode.ADD_QUESTION_CONVERSATION -> {
                    SubjectQuestionConversationSheet(
                        subjectName = openSubject.name,
                        hasTextbook = openSubject.hasTextbookContent(),
                        messages = subjectQuestionMessages,
                        isThinking = isSubjectQuestionThinking,
                        draftState = subjectQuestionDraftState,
                        maxFontSizeSp = maxFontSizeSp,
                        onDismiss = {
                            onClearSubjectQuestionChat()
                            activeSheetMode = SubjectSheetMode.NONE
                        },
                        onSendMessage = { text, bitmaps ->
                            onSendSubjectQuestionMessage(openSubject.id, text, bitmaps)
                        },
                        onSendAudioMessage = { audioBase64, bitmaps ->
                            onSendSubjectQuestionAudio(openSubject.id, audioBase64, bitmaps)
                        },
                        onApplyCorrection = onApplySubjectQuestionCorrection,
                        onGenerateQuestion = {
                            onGenerateDraftQuestionFromConversation(openSubject.id)
                        },
                        onDismissDraft = onDismissSubjectQuestionDraft,
                        onAcceptDraft = {
                            if (onSaveGeneratedSubjectQuestion(openSubject.id)) {
                                activeSheetMode = SubjectSheetMode.NONE
                            }
                        }
                    )
                }

                SubjectSheetMode.TEXTBOOK -> {
                    val textbookImages = remember(openSubject.textbookImagesJson) {
                        openSubject.textbookImages()
                    }
                    SubjectMaterialSheet(
                        title = "Textbook",
                        subtitle = "Answer reference — paste text or drop pages below (DeepSeek OCR extracts them automatically).",
                        placeholder = "Paste or type your textbook chapters, core definitions, or authoritative study notes here — or drop page photos in the boxes below…",
                        initialValue = openSubject.textbook,
                        images = textbookImages,
                        onAddCameraImage = { bitmap ->
                            onAddMaterialCameraImage(
                                openSubject.id,
                                SubjectMaterialKind.TEXTBOOK,
                                bitmap
                            )
                        },
                        onAddGalleryImages = { uris ->
                            onAddMaterialGalleryImages(
                                openSubject.id,
                                SubjectMaterialKind.TEXTBOOK,
                                uris
                            )
                        },
                        onDeleteImage = { imageId ->
                            onDeleteMaterialImage(
                                openSubject.id,
                                SubjectMaterialKind.TEXTBOOK,
                                imageId
                            )
                        },
                        onRetryOcr = { imageId ->
                            onRetryMaterialImageOcr(
                                openSubject.id,
                                SubjectMaterialKind.TEXTBOOK,
                                imageId
                            )
                        },
                        onDismiss = { activeSheetMode = SubjectSheetMode.NONE },
                        onSave = { updated ->
                            onUpdateTextbook(openSubject.id, updated)
                            activeSheetMode = SubjectSheetMode.NONE
                        }
                    )
                }

                SubjectSheetMode.EXTRA_KNOWLEDGE -> {
                    val extraImages = remember(openSubject.extraKnowledgeImagesJson) {
                        openSubject.extraKnowledgeImages()
                    }
                    SubjectMaterialSheet(
                        title = "Extra knowledge",
                        subtitle = "Backing context — you do NOT have to state this in your answers, and the tutor can quote from text or OCR'd images when helpful.",
                        placeholder = "Paste or type background context, examples, historical notes, or quotable passages — or drop images in the boxes below…",
                        initialValue = openSubject.extraKnowledge,
                        images = extraImages,
                        onAddCameraImage = { bitmap ->
                            onAddMaterialCameraImage(
                                openSubject.id,
                                SubjectMaterialKind.EXTRA_KNOWLEDGE,
                                bitmap
                            )
                        },
                        onAddGalleryImages = { uris ->
                            onAddMaterialGalleryImages(
                                openSubject.id,
                                SubjectMaterialKind.EXTRA_KNOWLEDGE,
                                uris
                            )
                        },
                        onDeleteImage = { imageId ->
                            onDeleteMaterialImage(
                                openSubject.id,
                                SubjectMaterialKind.EXTRA_KNOWLEDGE,
                                imageId
                            )
                        },
                        onRetryOcr = { imageId ->
                            onRetryMaterialImageOcr(
                                openSubject.id,
                                SubjectMaterialKind.EXTRA_KNOWLEDGE,
                                imageId
                            )
                        },
                        onDismiss = { activeSheetMode = SubjectSheetMode.NONE },
                        onSave = { updated ->
                            onUpdateExtraKnowledge(openSubject.id, updated)
                            activeSheetMode = SubjectSheetMode.NONE
                        }
                    )
                }
            }
        }

        subjectPendingDelete?.let { subject ->
            val questionCount = cards.count { it.subjectId == subject.id }
            AlertDialog(
                onDismissRequest = { subjectPendingDelete = null },
                title = { Text("Delete this subject?") },
                text = {
                    Text(
                        "\"${subject.name}\" and its $questionCount question" +
                            (if (questionCount == 1) "" else "s") + " will be removed.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (openSubjectId == subject.id) {
                            openSubjectId = null
                            activeSheetMode = SubjectSheetMode.NONE
                        }
                        onDeleteSubject(subject)
                        subjectPendingDelete = null
                    }) {
                        Text("Delete", color = EvalRedDot)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { subjectPendingDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark
            )
        }
    }
}

@Composable
private fun SubjectListContent(
    subjects: List<Subject>,
    cards: List<Flashcard>,
    onCreate: () -> Unit,
    onOpen: (Subject) -> Unit,
    onDelete: (Subject) -> Unit
) {
    val now = rememberCurrentTimeMillis()

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = PrimaryPurple,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("create_subject_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New subject",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subjects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark)
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
                            .padding(24.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "No subjects yet",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap + to name and save a subject, then open it to manage questions, textbook reference, and extra knowledge.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(subjects, key = { it.id }) { subject ->
                val subjectCards = cards.filter { it.subjectId == subject.id }
                val activeCards = subjectCards.filter { it.isActive }
                val learnedCount = subjectCards.count { it.isLearned }
                val dumbCount = subjectCards.count { it.isDumb }
                val due = activeCards.count { SpacedRepetitionUtils.isCardDue(it, now) }
                val nextDueInLabel = if (due == 0 && activeCards.isNotEmpty()) {
                    activeCards.minOfOrNull { it.nextReviewTimeMillis }?.let { earliest ->
                        SpacedRepetitionUtils.formatRemainingDuration(earliest - now)
                    }
                } else {
                    null
                }
                SubjectRow(
                    subject = subject,
                    activeCount = activeCards.size,
                    learnedCount = learnedCount,
                    dumbCount = dumbCount,
                    dueCount = due,
                    nextDueInLabel = nextDueInLabel,
                    onClick = { onOpen(subject) },
                    onDelete = { onDelete(subject) }
                )
            }
        }
    }
}

@Composable
private fun SubjectRow(
    subject: Subject,
    activeCount: Int,
    learnedCount: Int,
    dumbCount: Int,
    dueCount: Int,
    nextDueInLabel: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("subject_row_${subject.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                BidiText(
                    text = subject.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("$activeCount active")
                        when {
                            dueCount > 0 -> append(" · $dueCount due")
                            nextDueInLabel != null -> append(" · due in $nextDueInLabel")
                        }
                        if (learnedCount > 0) append(" · $learnedCount learned")
                        if (dumbCount > 0) append(" · $dumbCount dumb")
                    },
                    color = if (dueCount > 0) PrimaryPurpleLight else TextMuted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete subject",
                    tint = AccentPink.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SubjectDetailContent(
    subject: Subject,
    cards: List<Flashcard>,
    isGeneratingQuestions: Boolean,
    generationProgress: SubjectGenerationProgress?,
    statusNotice: SubjectStatusNotice?,
    onBack: () -> Unit,
    onStartReview: () -> Unit,
    onGenerateQuestions: () -> Unit,
    onCopyQuestionBank: () -> Unit,
    onDeleteQuestionBank: () -> Unit,
    onOpenAddQuestion: () -> Unit,
    onOpenTextbook: () -> Unit,
    onOpenExtraKnowledge: () -> Unit,
    onDismissStatus: () -> Unit,
    onRestoreQuestion: (Flashcard) -> Unit,
    onDeleteQuestion: (Flashcard) -> Unit
) {
    val nowMillis = rememberCurrentTimeMillis()
    val activeCards = remember(cards) { cards.filter { it.isActive } }
    val learnedCount = remember(cards) { cards.count { it.isLearned } }
    val dumbCount = remember(cards) { cards.count { it.isDumb } }
    val orderedCards = remember(cards) {
        cards.sortedWith(
            compareBy<Flashcard> {
                when {
                    it.isActive -> 0
                    it.isLearned -> 1
                    else -> 2
                }
            }.thenBy { it.createdAtMillis }
        )
    }

    var justCopiedCount by remember(subject.id) { mutableStateOf<Int?>(null) }
    var showDeleteQuestionBankConfirm by remember(subject.id) { mutableStateOf(false) }
    LaunchedEffect(justCopiedCount) {
        if (justCopiedCount != null) {
            kotlinx.coroutines.delay(2200L)
            justCopiedCount = null
        }
    }

    val dueCount = activeCards.count { SpacedRepetitionUtils.isCardDue(it, nowMillis) }
    val nextDueInLabel = if (dueCount == 0 && activeCards.isNotEmpty()) {
        activeCards.minOfOrNull { it.nextReviewTimeMillis }?.let { earliest ->
            SpacedRepetitionUtils.formatRemainingDuration(earliest - nowMillis)
        }
    } else {
        null
    }

    val textbookWordCount = remember(subject.textbook) { countWords(subject.textbook) }
    val extraKnowledgeWordCount = remember(subject.extraKnowledge) { countWords(subject.extraKnowledge) }
    val textbookImages = remember(subject.textbookImagesJson) { subject.textbookImages() }
    val extraKnowledgeImages = remember(subject.extraKnowledgeImagesJson) { subject.extraKnowledgeImages() }
    val hasTextbook = textbookWordCount > 0 || textbookImages.isNotEmpty()
    val hasExtraKnowledge = extraKnowledgeWordCount > 0 || extraKnowledgeImages.isNotEmpty()

    Scaffold(
        containerColor = BgDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("subject_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to subjects",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        BidiText(
                            text = subject.name,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("${activeCards.size} active · $dueCount due")
                                if (learnedCount > 0) append(" · $learnedCount learned")
                                if (dumbCount > 0) append(" · $dumbCount dumb")
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 1. Start the review
            item {
                Button(
                    onClick = {
                        if (dueCount > 0) onStartReview()
                    },
                    enabled = dueCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryPurple,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = SurfaceCard,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("subject_start_review_button")
                ) {
                    Icon(
                        imageVector = if (dueCount > 0) Icons.Default.PlayArrow else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            dueCount > 0 -> "Start the review ($dueCount due)"
                            activeCards.isEmpty() -> "Start the review"
                            nextDueInLabel != null -> "Start the review · in $nextDueInLabel"
                            else -> "Start the review"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // 2. Generating questions  |  3. Add a question
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectTopActionTile(
                        title = stringResource(R.string.subject_generate_questions_button),
                        subtitle = when {
                            isGeneratingQuestions && generationProgress?.step == 2 -> {
                                val s2Added = generationProgress.step2AddedCount
                                val s2Edited = generationProgress.editedCount
                                val s2Combined = generationProgress.combinedCount
                                if (s2Added > 0 || s2Edited > 0 || s2Combined > 0) {
                                    buildString {
                                        append("Step 2/2 ·")
                                        if (s2Added > 0) append(" +$s2Added")
                                        if (s2Edited > 0) append(" · ${s2Edited}fx")
                                        if (s2Combined > 0) append(" · ${s2Combined}mg")
                                    }
                                } else {
                                    "Step 2/2 · auditing…"
                                }
                            }
                            isGeneratingQuestions && (generationProgress?.addedCount ?: 0) > 0 ->
                                "Step 1/2 · +${generationProgress?.addedCount} streaming…"
                            isGeneratingQuestions && (generationProgress?.reasoningChars ?: 0) > 0 ->
                                "Step 1/2 · thinking…"
                            isGeneratingQuestions -> "Step 1/2 · starting…"
                            hasTextbook && cards.isEmpty() ->
                                stringResource(R.string.subject_generate_questions_idle_empty)
                            hasTextbook ->
                                stringResource(R.string.subject_generate_questions_idle_existing)
                            else -> stringResource(R.string.subject_generate_questions_no_textbook)
                        },
                        icon = Icons.Default.AutoAwesome,
                        isLoading = isGeneratingQuestions,
                        enabled = !isGeneratingQuestions,
                        onClick = onGenerateQuestions,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_generate_questions_button")
                    )

                    SubjectTopActionTile(
                        title = "Add a question",
                        subtitle = if (hasTextbook) "Refs textbook" else "AI conversation",
                        icon = Icons.Default.ChatBubbleOutline,
                        onClick = onOpenAddQuestion,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_add_question_button")
                    )
                }
            }

            // 4. Textbook (answer reference)  |  5. Extra knowledge (backing & quotable)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectTopActionTile(
                        title = "Textbook",
                        subtitle = formatMaterialTileSubtitle(
                            prefix = "Answer ref",
                            defaultLabel = "Answer reference",
                            wordCount = textbookWordCount,
                            images = textbookImages
                        ),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        indicatorDotColor = if (hasTextbook) EvalGreenDot else null,
                        onClick = onOpenTextbook,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_textbook_button")
                    )

                    SubjectTopActionTile(
                        title = "Extra knowledge",
                        subtitle = formatMaterialTileSubtitle(
                            prefix = "Backing",
                            defaultLabel = "Backing & quotes",
                            wordCount = extraKnowledgeWordCount,
                            images = extraKnowledgeImages
                        ),
                        icon = Icons.Default.Lightbulb,
                        indicatorDotColor = if (hasExtraKnowledge) EvalBlueDot else null,
                        onClick = onOpenExtraKnowledge,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_extra_knowledge_button")
                    )
                }
            }

            // 6. Copy Question Bank  |  7. Delete Question Bank
            item {
                val copiedCount = justCopiedCount
                val copySubtitle = when {
                    copiedCount != null ->
                        "Copied $copiedCount question${if (copiedCount == 1) "" else "s"}"
                    cards.isNotEmpty() ->
                        "${cards.size} total · ${activeCards.size} active"
                    else -> stringResource(R.string.subject_copy_question_bank_empty_subtitle)
                }
                val copyDotColor = when {
                    copiedCount != null -> EvalGreenDot
                    cards.isNotEmpty() -> PrimaryPurpleLight
                    else -> null
                }
                val deleteSubtitle = when {
                    isGeneratingQuestions -> "Wait for generation…"
                    cards.isNotEmpty() ->
                        "Clear all ${cards.size} question${if (cards.size == 1) "" else "s"}"
                    else -> stringResource(R.string.subject_delete_question_bank_empty_subtitle)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectTopActionTile(
                        title = stringResource(R.string.subject_copy_question_bank_button),
                        subtitle = copySubtitle,
                        icon = Icons.Default.ContentCopy,
                        indicatorDotColor = copyDotColor,
                        onClick = {
                            if (cards.isNotEmpty()) {
                                justCopiedCount = cards.size
                            }
                            onCopyQuestionBank()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_copy_question_bank_button")
                    )

                    SubjectTopActionTile(
                        title = stringResource(R.string.subject_delete_question_bank_button),
                        subtitle = deleteSubtitle,
                        icon = Icons.Default.DeleteSweep,
                        iconTint = if (cards.isNotEmpty() && !isGeneratingQuestions) {
                            AccentPink.copy(alpha = 0.9f)
                        } else {
                            TextMuted
                        },
                        indicatorDotColor = if (cards.isNotEmpty()) EvalRedDot else null,
                        enabled = !isGeneratingQuestions,
                        onClick = {
                            if (cards.isNotEmpty()) {
                                showDeleteQuestionBankConfirm = true
                            } else {
                                onDeleteQuestionBank()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subject_delete_question_bank_button")
                    )
                }
            }

            if (isGeneratingQuestions) {
                item {
                    val currentStep = generationProgress?.step ?: 1
                    val addedSoFar = generationProgress?.addedCount ?: 0
                    val step2Added = generationProgress?.step2AddedCount ?: 0
                    val editedSoFar = generationProgress?.editedCount ?: 0
                    val combinedSoFar = generationProgress?.combinedCount ?: 0

                    val liveBannerText = when {
                        currentStep == 2 && (step2Added > 0 || editedSoFar > 0 || combinedSoFar > 0) -> {
                            buildString {
                                append("Step 2/2 refining bank live — ")
                                val actions = mutableListOf<String>()
                                if (step2Added > 0) {
                                    actions.add("+$step2Added complementary added")
                                }
                                if (editedSoFar > 0) {
                                    actions.add("$editedSoFar fixed")
                                }
                                if (combinedSoFar > 0) {
                                    actions.add("$combinedSoFar combined")
                                }
                                append(actions.joinToString(", "))
                                if (addedSoFar > step2Added) {
                                    append(" (${addedSoFar - step2Added} added in Step 1)")
                                }
                                append("…")
                            }
                        }
                        currentStep == 2 -> {
                            if (addedSoFar > 0) {
                                "Step 2/2 — Step 1 added $addedSoFar question${if (addedSoFar == 1) "" else "s"}; now auditing to add missing angles, fix unclear questions, and combine fragments…"
                            } else {
                                stringResource(R.string.subject_generate_step2_thinking_banner)
                            }
                        }
                        addedSoFar > 0 -> {
                            "Step 1/2 streaming live — $addedSoFar question${if (addedSoFar == 1) "" else "s"} appended so far (Step 2 audit & complement runs next)…"
                        }
                        else -> {
                            stringResource(R.string.subject_generate_step1_thinking_banner)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EvalBlueBg)
                            .border(1.dp, EvalBlueBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = EvalBlueDot
                        )
                        Text(
                            text = liveBannerText,
                            color = EvalBlueDot,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (statusNotice != null) {
                item {
                    val bg = when {
                        statusNotice.isError -> EvalRedBg
                        statusNotice.isWarning -> EvalBlueBg
                        else -> EvalGreenBg
                    }
                    val border = when {
                        statusNotice.isError -> EvalRedBorder
                        statusNotice.isWarning -> EvalBlueBorder
                        else -> EvalGreenBorder
                    }
                    val accent = when {
                        statusNotice.isError -> EvalRedDot
                        statusNotice.isWarning -> EvalBlueDot
                        else -> EvalGreenDot
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusNotice.text,
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissStatus,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss message",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Questions",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            if (cards.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Nothing here yet",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Save your Textbook reference to generate questions automatically, or tap Add a question to build one through conversation.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

                items(orderedCards, key = { it.id }) { card ->
                val isDue = SpacedRepetitionUtils.isCardDue(card, nowMillis)
                val remainingLabel = if (card.isActive && !isDue) {
                    SpacedRepetitionUtils.formatRemainingDuration(card.nextReviewTimeMillis - nowMillis)
                } else {
                    null
                }
                QuestionRow(
                    card = card,
                    isDue = isDue,
                    remainingLabel = remainingLabel,
                    onRestore = { onRestoreQuestion(card) },
                    onDelete = { onDeleteQuestion(card) }
                )
            }
        }
    }

    if (showDeleteQuestionBankConfirm) {
        val questionCount = cards.size
        AlertDialog(
            onDismissRequest = { showDeleteQuestionBankConfirm = false },
            title = {
                Text(stringResource(R.string.subject_delete_question_bank_confirm_title))
            },
            text = {
                Text(
                    text = "All $questionCount question${if (questionCount == 1) "" else "s"} (active, learned, and dumb) in \"${subject.name}\" will be permanently removed. Your Textbook and Extra knowledge will remain intact.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        justCopiedCount = null
                        showDeleteQuestionBankConfirm = false
                        onDeleteQuestionBank()
                    },
                    modifier = Modifier.testTag("confirm_delete_question_bank_button")
                ) {
                    Text(
                        text = stringResource(R.string.subject_delete_question_bank_confirm_action),
                        color = EvalRedDot
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteQuestionBankConfirm = false },
                    modifier = Modifier.testTag("cancel_delete_question_bank_button")
                ) {
                    Text(
                        text = stringResource(R.string.subject_delete_question_bank_cancel_action),
                        color = TextSecondary
                    )
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun SubjectTopActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    iconTint: Color = PrimaryPurpleLight,
    indicatorDotColor: Color? = null
) {
    Box(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = iconTint
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (indicatorDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(indicatorDotColor)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuestionRow(
    card: Flashcard,
    isDue: Boolean,
    remainingLabel: String?,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isInactive = !card.isActive
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isInactive -> SurfaceCard.copy(alpha = 0.45f)
                    isDue -> SurfaceCard
                    else -> SurfaceCard.copy(alpha = 0.65f)
                }
            )
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BidiText(
                    text = card.word,
                    color = if (isInactive) TextSecondary else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        card.isLearned -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EvalGreenBg)
                                    .border(1.dp, EvalGreenBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Learned · inactive",
                                    color = EvalGreenDot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceInput)
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRestore)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("restore_question_${card.id}")
                            ) {
                                Text(
                                    text = "Activate",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        card.isDumb -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EvalRedBg)
                                    .border(1.dp, EvalRedBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Dumb · inactive",
                                    color = EvalRedDot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceInput)
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRestore)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("restore_question_${card.id}")
                            ) {
                                Text(
                                    text = "Activate",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDue) BadgeGreenBg else SurfaceInput)
                                    .border(
                                        1.dp,
                                        if (isDue) BadgeGreenBorder else SurfaceCardBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (isDue) "Due now" else "Locked · due in ${remainingLabel ?: "later"}",
                                    color = if (isDue) BadgeGreenText else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (card.type == FlashcardTypes.CONVERSATION_TECHNICAL) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EvalBlueBg)
                                .border(1.dp, EvalBlueBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Conversation",
                                color = EvalBlueDot,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete question",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
private fun CreateSubjectSheet(
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var isFieldActive by remember { mutableStateOf(true) }
    var selection by remember { mutableIntStateOf(0) }

    val canSave = name.isNotBlank()

    BackHandler { onDismiss() }
    BackHandler(enabled = isFieldActive) {
        isFieldActive = false
    }

    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
    ) {
        KeyboardDismissSurface(
            isKeyboardVisible = isFieldActive,
            onDismissKeyboard = { isFieldActive = false },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "New subject",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceInput)
                        .border(
                            width = 1.dp,
                            color = if (isFieldActive) PrimaryPurple else SurfaceCardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .virtualFieldContainer {
                            isFieldActive = true
                            selection = name.length
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag("create_subject_name_input")
                ) {
                    VirtualTextField(
                        text = name,
                        selection = selection,
                        isActive = isFieldActive,
                        onActivate = { offset ->
                            isFieldActive = true
                            selection = offset
                        },
                        placeholder = "Subject name (e.g. Biology, Operating Systems)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        onPaste = { clip ->
                            name = clip.lineSequence().firstOrNull().orEmpty().trim()
                            selection = name.length
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("create_subject_cancel_button"),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { if (canSave) onSave(name.trim()) },
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("create_subject_save_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            CreationVirtualKeyboard(
                visible = isFieldActive,
                text = TextFieldValue(
                    name,
                    TextRange(selection.coerceIn(0, name.length))
                ),
                onTextChange = { updated ->
                    name = updated.text.replace("\n", " ")
                    selection = updated.selection.end.coerceIn(0, name.length)
                },
                onSend = {
                    if (name.isNotBlank()) {
                        onSave(name.trim())
                    } else {
                        isFieldActive = false
                    }
                },
                showInputField = false,
                sendLabel = "Save",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SubjectMaterialSheet(
    title: String,
    subtitle: String,
    placeholder: String,
    initialValue: String,
    images: List<SubjectImageAttachment>,
    onAddCameraImage: (Bitmap) -> Unit,
    onAddGalleryImages: (List<Uri>) -> Unit,
    onDeleteImage: (String) -> Unit,
    onRetryOcr: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var content by rememberSaveable(title) { mutableStateOf(initialValue) }
    var isFieldActive by remember { mutableStateOf(false) }
    var selection by remember { mutableIntStateOf(initialValue.length) }
    val context = LocalContext.current

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!clip.isNullOrBlank()) {
            content = if (content.isBlank()) clip else "$content\n\n$clip"
            selection = content.length
            isFieldActive = true
        }
    }

    BackHandler { onDismiss() }
    BackHandler(enabled = isFieldActive) {
        isFieldActive = false
    }

    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
    ) {
        KeyboardDismissSurface(
            isKeyboardVisible = isFieldActive,
            onDismissKeyboard = { isFieldActive = false },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val words = countWords(content)
                    val ocrWords = remember(images) {
                        images.sumOf { countWords(it.ocrText) }
                    }
                    Text(
                        text = buildString {
                            when {
                                words > 0 && images.isNotEmpty() -> {
                                    append("$words words · ${images.size} img")
                                    if (ocrWords > 0) append(" ($ocrWords OCR words)")
                                }
                                words > 0 -> append("$words words")
                                images.isNotEmpty() -> {
                                    append("${images.size} image${if (images.size == 1) "" else "s"}")
                                    if (ocrWords > 0) append(" · $ocrWords OCR words")
                                }
                                else -> append("Empty")
                            }
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (content.isNotBlank()) {
                            TextButton(onClick = {
                                content = ""
                                selection = 0
                            }) {
                                Text("Clear", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { pasteFromClipboard() }) {
                            Text("Paste", color = PrimaryPurpleLight, fontSize = 12.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceInput)
                        .border(
                            width = 1.dp,
                            color = if (isFieldActive) PrimaryPurple else SurfaceCardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .virtualFieldContainer {
                            isFieldActive = true
                            selection = content.length
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("subject_material_input")
                ) {
                    VirtualTextField(
                        text = content,
                        selection = selection,
                        isActive = isFieldActive,
                        onActivate = { offset ->
                            isFieldActive = true
                            selection = offset
                        },
                        placeholder = placeholder,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        onPaste = { clip ->
                            content = if (content.isBlank()) clip else "$content\n\n$clip"
                            selection = content.length
                        }
                    )
                }

                SubjectMaterialImagesSection(
                    images = images,
                    onAddCameraImage = onAddCameraImage,
                    onAddGalleryImages = onAddGalleryImages,
                    onDeleteImage = onDeleteImage,
                    onRetryOcr = onRetryOcr,
                    onDismissKeyboard = { isFieldActive = false }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(content.trim()) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("subject_material_save_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }

            CreationVirtualKeyboard(
                visible = isFieldActive,
                text = TextFieldValue(
                    content,
                    TextRange(selection.coerceIn(0, content.length))
                ),
                onTextChange = { updated ->
                    content = updated.text
                    selection = updated.selection.end
                },
                onSend = { isFieldActive = false },
                showInputField = false,
                sendLabel = "Done",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SubjectQuestionConversationSheet(
    subjectName: String,
    hasTextbook: Boolean,
    messages: List<ChatMessage>,
    isThinking: Boolean,
    draftState: SubjectQuestionDraftState,
    maxFontSizeSp: Int,
    onDismiss: () -> Unit,
    onSendMessage: (text: String, imageBitmaps: List<Bitmap>) -> Unit,
    onSendAudioMessage: (audioBase64: String, imageBitmaps: List<Bitmap>) -> Unit,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit,
    onGenerateQuestion: () -> Unit,
    onDismissDraft: () -> Unit,
    onAcceptDraft: () -> Unit
) {
    var isKeyboardVisible by remember { mutableStateOf(false) }
    val isDraftActive = draftState !is SubjectQuestionDraftState.Idle

    LaunchedEffect(isDraftActive) {
        if (isDraftActive) {
            isKeyboardVisible = false
        }
    }

    BackHandler { onDismiss() }
    BackHandler(enabled = isKeyboardVisible) {
        isKeyboardVisible = false
    }
    BackHandler(enabled = isDraftActive) {
        onDismissDraft()
    }

    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            KeyboardDismissSurface(
                isKeyboardVisible = isKeyboardVisible,
                onDismissKeyboard = { isKeyboardVisible = false },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to subject",
                                tint = TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add a question",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            BidiText(
                                text = if (hasTextbook) {
                                    "$subjectName · Textbook referenced"
                                } else {
                                    subjectName
                                },
                                color = if (hasTextbook) EvalGreenDot else TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ConversationCreationView(
                            messages = messages,
                            isThinking = isThinking,
                            maxFontSizeSp = maxFontSizeSp,
                            isKeyboardVisible = isKeyboardVisible,
                            onKeyboardVisibleChange = { isKeyboardVisible = it },
                            onSendMessage = onSendMessage,
                            onSendAudioMessage = onSendAudioMessage,
                            onApplyCorrection = onApplyCorrection,
                            emptyLabel = if (hasTextbook) {
                                "Tap to type, hold to speak, or add image(s) about any part of your Textbook, then tap Generate the question."
                            } else {
                                "Tap to type, hold to speak, or add image(s) about the specific question you want to generate."
                            },
                            bottomActions = { canSubmit, onAddImageClick, pendingImageCount ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .navigationBarsPadding()
                                        .padding(top = 8.dp, bottom = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onAddImageClick,
                                        enabled = !isThinking && !isDraftActive,
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.dp, SurfaceCardBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .heightIn(min = 48.dp)
                                            .testTag("subject_conversation_add_image_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Add image",
                                            tint = PrimaryPurpleLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (pendingImageCount > 0) {
                                                "Add image ($pendingImageCount)"
                                            } else {
                                                "Add image"
                                            },
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = onGenerateQuestion,
                                        enabled = canSubmit && !isDraftActive,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryPurple,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 48.dp)
                                            .testTag("subject_conversation_add_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.subject_generate_the_question_button),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (isDraftActive) {
                SubjectGeneratedQuestionPreviewOverlay(
                    subjectName = subjectName,
                    draftState = draftState,
                    onBackToChat = onDismissDraft,
                    onRegenerate = onGenerateQuestion,
                    onSave = onAcceptDraft
                )
            }
        }
    }
}

@Composable
internal fun SubjectGeneratedQuestionPreviewOverlay(
    subjectName: String,
    draftState: SubjectQuestionDraftState,
    onBackToChat: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    generatingSubtitle: String = stringResource(R.string.subject_generating_single_question_subtitle)
) {
    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .testTag("subject_question_draft_overlay")
    ) {
        when (draftState) {
            SubjectQuestionDraftState.Idle -> Unit

            is SubjectQuestionDraftState.Generating -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 22.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryPurpleLight,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(R.string.subject_generating_single_question_title),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (draftState.reasoningChars > 0) {
                                    "Deep thinking… formulating the exact question and reference answer."
                                } else {
                                    generatingSubtitle
                                },
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = onBackToChat,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("subject_draft_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_cancel_action),
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is SubjectQuestionDraftState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackToChat) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.subject_preview_back_to_chat),
                                tint = TextPrimary
                            )
                        }
                        Text(
                            text = stringResource(R.string.subject_preview_question_header),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(EvalRedBg)
                            .border(1.dp, EvalRedBorder, RoundedCornerShape(18.dp))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = draftState.message,
                            color = EvalRedDot,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToChat,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("subject_draft_back_button")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_preview_back_to_chat),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = onRegenerate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("subject_draft_retry_button")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_preview_regenerate),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            is SubjectQuestionDraftState.AlreadyExists -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackToChat) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.subject_preview_back_to_chat),
                                tint = TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.subject_duplicate_question_header),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            BidiText(
                                text = subjectName,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(EvalBlueBg)
                                .border(1.dp, EvalBlueBorder, RoundedCornerShape(18.dp))
                                .padding(18.dp)
                                .testTag("subject_duplicate_question_notice")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_duplicate_question_message),
                                color = EvalBlueDot,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp
                            )
                        }

                        if (!draftState.matchedQuestion.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                                    .testTag("subject_duplicate_matched_card")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val statusSuffix = when (draftState.matchedStatus) {
                                        "LEARNED" -> " · Learned"
                                        "DUMB" -> " · Dumb"
                                        "ACTIVE" -> " · Active"
                                        else -> ""
                                    }
                                    Text(
                                        text = stringResource(R.string.subject_duplicate_matched_label) + statusSuffix,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    BidiText(
                                        text = draftState.matchedQuestion,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        lineHeight = 23.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .padding(top = 8.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBackToChat,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("subject_duplicate_back_button")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_preview_back_to_chat),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            is SubjectQuestionDraftState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackToChat) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.subject_preview_back_to_chat),
                                tint = TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.subject_preview_question_header),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            BidiText(
                                text = subjectName,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = onRegenerate,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("subject_draft_regenerate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.subject_preview_regenerate),
                                tint = PrimaryPurpleLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.subject_preview_regenerate),
                                color = PrimaryPurpleLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceCard)
                                .border(1.5.dp, PrimaryPurple.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                                .padding(16.dp)
                                .testTag("subject_generated_question_card")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.subject_preview_question_label),
                                    color = PrimaryPurpleLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                BidiText(
                                    text = draftState.question,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 24.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(EvalGreenBg)
                                .border(1.dp, EvalGreenBorder, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                                .testTag("subject_generated_answer_card")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.subject_preview_answer_label),
                                    color = EvalGreenDot,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                BidiText(
                                    text = draftState.answer,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .padding(top = 8.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToChat,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("subject_draft_back_button")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_preview_back_to_chat),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("subject_draft_save_button")
                        ) {
                            Text(
                                text = stringResource(R.string.subject_preview_save),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMaterialTileSubtitle(
    prefix: String,
    defaultLabel: String,
    wordCount: Int,
    images: List<SubjectImageAttachment>
): String {
    val processing = images.any { it.isProcessingOcr }
    if (processing) {
        return "$prefix · ${images.size} img · OCR…"
    }
    return when {
        wordCount > 0 && images.isNotEmpty() ->
            "$prefix · $wordCount w · ${images.size} img"
        images.isNotEmpty() -> {
            val ocrWords = images.sumOf { countWords(it.ocrText) }
            if (ocrWords > 0) {
                "$prefix · ${images.size} img · $ocrWords w"
            } else {
                "$prefix · ${images.size} image${if (images.size == 1) "" else "s"}"
            }
        }
        wordCount > 0 -> "$prefix · $wordCount words"
        else -> defaultLabel
    }
}

private fun countWords(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    return trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }
}

