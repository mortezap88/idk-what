package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notepad.NotepadPage
import com.example.ui.AddFlashcardSheet
import com.example.ui.HomeScreen
import com.example.ui.MainViewModel
import com.example.ui.ReviewExtractQuestionSheet
import com.example.ui.ReviewScreen
import com.example.ui.SettingsScreen
import com.example.ui.SubjectCentreScreen
import com.example.ui.theme.BgDark
import com.example.ui.theme.MyApplicationTheme

private const val PAGE_STAGES = 0
private const val PAGE_SUBJECTS = 1
private const val PAGE_NOTES = 2
private const val PAGE_SETTINGS = 3
private const val PAGE_COUNT = 4

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            MyApplicationTheme(themePreference = appSettings.theme) {
                FlashRecallApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FlashRecallApp(viewModel: MainViewModel) {
    val allCards by viewModel.allCards.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val activeReviewList by viewModel.activeReviewList.collectAsStateWithLifecycle()
    val currentReviewIndex by viewModel.currentReviewIndex.collectAsStateWithLifecycle()
    val currentScenarioOrQuestion by viewModel.currentScenarioOrQuestion.collectAsStateWithLifecycle()
    val reviewChatHistory by viewModel.reviewChatHistory.collectAsStateWithLifecycle()
    val reviewScenarioLoading by viewModel.reviewScenarioLoading.collectAsStateWithLifecycle()
    val isReviewReplying by viewModel.isReviewReplying.collectAsStateWithLifecycle()
    val generatingQuestionsSubjectIds by viewModel.generatingQuestionsSubjectIds.collectAsStateWithLifecycle()
    val generatingQuestionProgress by viewModel.generatingQuestionProgress.collectAsStateWithLifecycle()
    val subjectStatusMessages by viewModel.subjectStatusMessages.collectAsStateWithLifecycle()
    val subjectQuestionChatMessages by viewModel.subjectQuestionChatMessages.collectAsStateWithLifecycle()
    val isSubjectQuestionThinking by viewModel.isSubjectQuestionThinking.collectAsStateWithLifecycle()
    val subjectQuestionDraftState by viewModel.subjectQuestionDraftState.collectAsStateWithLifecycle()
    val creationChatMessages by viewModel.creationChatMessages.collectAsStateWithLifecycle()
    val isCreationThinking by viewModel.isCreationThinking.collectAsStateWithLifecycle()
    val isReviewExtractOpen by viewModel.isReviewExtractOpen.collectAsStateWithLifecycle()
    val isReviewExtractThinking by viewModel.isReviewExtractThinking.collectAsStateWithLifecycle()
    val reviewExtractChatMessages by viewModel.reviewExtractChatMessages.collectAsStateWithLifecycle()
    val reviewExtractDraftState by viewModel.reviewExtractDraftState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.ttsVoices.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }
    var isAddCardOpen by rememberSaveable { mutableStateOf(false) }

    val inReviewMode = activeReviewList.isNotEmpty() && currentReviewIndex in activeReviewList.indices

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == PAGE_SETTINGS) {
            viewModel.refreshTtsVoices()
        }
    }

    BackHandler(enabled = inReviewMode) {
        viewModel.endReviewSession()
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        if (inReviewMode) {
            ReviewScreen(
                cards = activeReviewList,
                currentIndex = currentReviewIndex,
                maxFontSizeSp = appSettings.maxFontSizeSp,
                scenarioLoading = reviewScenarioLoading,
                scenarioOrQuestion = currentScenarioOrQuestion,
                reviewChatHistory = reviewChatHistory,
                isReviewReplying = isReviewReplying,
                onSendReviewReply = { text -> viewModel.sendReviewReply(text) },
                onSendReviewAudioReply = { b64 -> viewModel.sendReviewAudioReply(b64) },
                onRetryReviewReply = { viewModel.retryLastReviewReply() },
                onApplyCorrection = { msgId, corrId -> viewModel.applyReviewCorrection(msgId, corrId) },
                onSelectStage = { card, stage -> viewModel.updateCardStage(card, stage) },
                onMarkDumb = { card -> viewModel.markCardDumb(card) },
                onMarkLearned = { card -> viewModel.markCardLearned(card) },
                onOpenExtractQuestion = { viewModel.openReviewExtractQuestion() },
                onSpeak = { text -> viewModel.speakText(text) },
                onClose = { viewModel.endReviewSession() }
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    PAGE_STAGES -> HomeScreen(
                        cards = allCards,
                        onOpenAdd = { isAddCardOpen = true },
                        onOpenReview = { stageIndex -> viewModel.startReviewSession(stageIndex) }
                    )

                    PAGE_SUBJECTS -> SubjectCentreScreen(
                        subjects = subjects,
                        cards = allCards,
                        maxFontSizeSp = appSettings.maxFontSizeSp,
                        generatingSubjectIds = generatingQuestionsSubjectIds,
                        generatingSubjectProgress = generatingQuestionProgress,
                        subjectStatusMessages = subjectStatusMessages,
                        subjectQuestionMessages = subjectQuestionChatMessages,
                        isSubjectQuestionThinking = isSubjectQuestionThinking,
                        subjectQuestionDraftState = subjectQuestionDraftState,
                        onCreateSubject = { name -> viewModel.createSubject(name) },
                        onUpdateTextbook = { id, text -> viewModel.updateSubjectTextbook(id, text) },
                        onUpdateExtraKnowledge = { id, text -> viewModel.updateSubjectExtraKnowledge(id, text) },
                        onAddMaterialCameraImage = { id, kind, bmp -> viewModel.addSubjectMaterialBitmap(id, kind, bmp) },
                        onAddMaterialGalleryImages = { id, kind, uris -> viewModel.addSubjectMaterialUris(id, kind, uris) },
                        onDeleteMaterialImage = { id, kind, imgId -> viewModel.deleteSubjectMaterialImage(id, kind, imgId) },
                        onRetryMaterialImageOcr = { id, kind, imgId -> viewModel.retrySubjectMaterialImageOcr(id, kind, imgId) },
                        onGenerateQuestions = { id -> viewModel.generateQuestionsForSubject(id) },
                        onCopyQuestionBank = { id -> viewModel.copySubjectQuestionBank(id) },
                        onDeleteQuestionBank = { id -> viewModel.deleteSubjectQuestionBank(id) },
                        onDismissSubjectStatus = { id -> viewModel.dismissSubjectStatus(id) },
                        onSendSubjectQuestionMessage = { id, text, bmps -> viewModel.sendSubjectQuestionMessage(id, text, bmps) },
                        onSendSubjectQuestionAudio = { id, b64, bmps -> viewModel.sendSubjectQuestionAudioMessage(id, b64, bmps) },
                        onApplySubjectQuestionCorrection = { msgId, corrId -> viewModel.applySubjectQuestionCorrection(msgId, corrId) },
                        onClearSubjectQuestionChat = { viewModel.clearSubjectQuestionChat() },
                        onGenerateDraftQuestionFromConversation = { id -> viewModel.generateDraftQuestionFromConversation(id) },
                        onDismissSubjectQuestionDraft = { viewModel.dismissSubjectQuestionDraft() },
                        onSaveGeneratedSubjectQuestion = { id -> viewModel.saveGeneratedSubjectQuestion(id) },
                        onDeleteSubject = { subject -> viewModel.deleteSubject(subject) },
                        onDeleteQuestion = { card -> viewModel.deleteQuestion(card) },
                        onRestoreQuestion = { card -> viewModel.restoreQuestion(card) },
                        onStartReview = { subjectId -> viewModel.startSubjectReview(subjectId) }
                    )

                    PAGE_NOTES -> NotepadPage()

                    PAGE_SETTINGS -> SettingsScreen(
                        settings = appSettings,
                        ttsVoices = ttsVoices,
                        onThemeSelected = { theme -> viewModel.saveTheme(theme) },
                        onTttProviderSelected = { provider -> viewModel.selectTttProvider(provider) },
                        onTttModelSelected = { modelId -> viewModel.selectTttModel(modelId) },
                        onSttModelSelected = { modelId -> viewModel.selectSttModel(modelId) },
                        onTokenStreamingSelected = { en -> viewModel.saveTokenStreaming(en) },
                        onMaxFontSizeSelected = { size -> viewModel.saveMaxFontSize(size) },
                        onTtsVoiceSelected = { voice -> viewModel.saveTtsVoice(voice) },
                        onTtsRateSelected = { rate -> viewModel.saveTtsRate(rate) },
                        onTestTts = { viewModel.testTts() },
                        onAddApiKey = { provider, key -> viewModel.addApiKey(provider, key) },
                        onRemoveApiKey = { provider, idx -> viewModel.removeApiKey(provider, idx) },
                        onClearApiKeys = { provider -> viewModel.clearApiKeys(provider) },
                        reviewItemCount = allCards.size,
                        subjectCount = subjects.size,
                        backupState = backupState,
                        onExportFlashData = { uri -> viewModel.exportFlashData(uri) },
                        onImportFlashData = { uri -> viewModel.importFlashData(uri) }
                    )
                }
            }
        }

        // Add Flashcard Bottom Sheet
        if (isAddCardOpen) {
            AddFlashcardSheet(
                onDismiss = { isAddCardOpen = false },
                onAddCard = { type, word, backText, imageUrl, convHist, focusSummary ->
                    viewModel.addFlashcard(
                        type = type,
                        word = word,
                        backText = backText,
                        imageUrl = imageUrl,
                        conversationHistory = convHist,
                        focusSummary = focusSummary
                    )
                },
                creationChatMessages = creationChatMessages,
                isCreationThinking = isCreationThinking,
                maxFontSizeSp = appSettings.maxFontSizeSp,
                onSendMessage = { text, bmps -> viewModel.sendCreationMessage(text, bmps) },
                onSendAudioMessage = { b64, bmps -> viewModel.sendCreationAudioMessage(b64, bmps) },
                onApplyCorrection = { msgId, corrId -> viewModel.applyCreationCorrection(msgId, corrId) },
                onClearChat = { viewModel.clearCreationChat() }
            )
        }

        // Review Extract Question Sheet
        if (isReviewExtractOpen) {
            val currentCard = activeReviewList.getOrNull(currentReviewIndex)
            val parentSubject = subjects.firstOrNull { it.id == currentCard?.subjectId }
            ReviewExtractQuestionSheet(
                subjectName = parentSubject?.name,
                hasTextbook = !parentSubject?.effectiveTextbook().isNullOrBlank(),
                hasExtraKnowledge = !parentSubject?.effectiveExtraKnowledge().isNullOrBlank(),
                messages = reviewExtractChatMessages,
                isThinking = isReviewExtractThinking,
                draftState = reviewExtractDraftState,
                maxFontSizeSp = appSettings.maxFontSizeSp,
                onDismiss = { viewModel.closeReviewExtractQuestion() },
                onSendMessage = { text, bmps -> viewModel.sendReviewExtractMessage(text, bmps) },
                onSendAudioMessage = { b64, bmps -> viewModel.sendReviewExtractAudioMessage(b64, bmps) },
                onApplyCorrection = { msgId, corrId -> viewModel.applyReviewExtractCorrection(msgId, corrId) },
                onGenerateQuestion = { viewModel.generateDraftQuestionFromReviewExtraction() },
                onDismissDraft = { viewModel.dismissReviewExtractDraft() },
                onAcceptDraft = { viewModel.saveExtractedReviewQuestion() }
            )
        }
    }
}
