package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.FlashcardTypes
import com.example.network.AvalAiClient
import com.example.network.ChatMessage
import com.example.network.TutorMessageFormatter
import com.example.notepad.ui.components.CameraCaptureDialog
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AddCardTab(val title: String) {
    TEXT("Text"),
    SPELLING("Spelling"),
    PRONUNCIATION("Pronunciation"),
    IMAGE("Image"),
    CONVERSATION("Conversation")
}

private enum class AddField { SPELLING, PRONUNCIATION, IMAGE, TEXT_FRONT, TEXT_BACK }

data class FieldState(
    val isActive: Boolean,
    val selection: Int,
    val onActivate: (Int) -> Unit
)

@Composable
fun AddFlashcardSheet(
    onDismiss: () -> Unit,
    onAddCard: (
        type: String,
        word: String,
        backText: String?,
        imageUrl: String?,
        conversationHistory: List<ChatMessage>?,
        focusSummary: String?
    ) -> Unit,
    creationChatMessages: List<ChatMessage>,
    isCreationThinking: Boolean,
    maxFontSizeSp: Int,
    onSendMessage: (text: String, imageBitmaps: List<Bitmap>) -> Unit,
    onSendAudioMessage: (audioBase64: String, imageBitmaps: List<Bitmap>) -> Unit,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit,
    onClearChat: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AddCardTab.TEXT) }

    // Inputs for regular tabs
    var wordInput by remember { mutableStateOf("") }
    var backInput by remember { mutableStateOf("") }
    var activeField by remember { mutableStateOf<AddField?>(null) }
    var activeSelection by remember { mutableIntStateOf(0) }
    var isConversationKeyboardVisible by remember { mutableStateOf(false) }
    var imageUrlInput by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageCardCameraDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageTabCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageCardCameraDialog = true
        } else {
            Toast.makeText(
                context,
                "Camera permission needed for photo capture",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val isAnyKeyboardOpen = activeField != null || isConversationKeyboardVisible

    BackHandler {
        onClearChat()
        onDismiss()
    }

    BackHandler(enabled = isAnyKeyboardOpen) {
        activeField = null
        isConversationKeyboardVisible = false
    }

    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        KeyboardDismissSurface(
            isKeyboardVisible = isAnyKeyboardOpen,
            onDismissKeyboard = {
                activeField = null
                isConversationKeyboardVisible = false
            },
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
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceInput)
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AddCardTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PrimaryPurple else Color.Transparent)
                                .clickable {
                                    selectedTab = tab
                                    activeField = null
                                    isConversationKeyboardVisible = false
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isFormKeyboardOpen = activeField != null
                    when (selectedTab) {
                        AddCardTab.TEXT -> {
                            TextAddView(
                                front = wordInput,
                                back = backInput,
                                isKeyboardOpen = isFormKeyboardOpen,
                                onDismissKeyboard = { activeField = null },
                                frontState = FieldState(
                                    isActive = activeField == AddField.TEXT_FRONT,
                                    selection = activeSelection,
                                    onActivate = { offset ->
                                        activeField = AddField.TEXT_FRONT
                                        activeSelection = offset
                                    }
                                ),
                                backState = FieldState(
                                    isActive = activeField == AddField.TEXT_BACK,
                                    selection = activeSelection,
                                    onActivate = { offset ->
                                        activeField = AddField.TEXT_BACK
                                        activeSelection = offset
                                    }
                                ),
                                onFrontChange = { wordInput = it },
                                onBackChange = { backInput = it },
                                onCancel = onDismiss,
                                onAdd = {
                                    if (wordInput.isNotBlank() && backInput.isNotBlank()) {
                                        onAddCard(
                                            FlashcardTypes.TEXT,
                                            wordInput,
                                            backInput,
                                            null,
                                            null,
                                            null
                                        )
                                        onDismiss()
                                    }
                                }
                            )
                        }
                        AddCardTab.SPELLING -> {
                            SpellingAddView(
                                word = wordInput,
                                isKeyboardOpen = isFormKeyboardOpen,
                                onDismissKeyboard = { activeField = null },
                                fieldState = FieldState(
                                    isActive = activeField == AddField.SPELLING,
                                    selection = activeSelection,
                                    onActivate = { offset ->
                                        activeField = AddField.SPELLING
                                        activeSelection = offset
                                    }
                                ),
                                onWordChange = { wordInput = it },
                                onCancel = onDismiss,
                                onAdd = {
                                    if (wordInput.isNotBlank()) {
                                        onAddCard(FlashcardTypes.SPELLING, wordInput, null, null, null, null)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                        AddCardTab.PRONUNCIATION -> {
                            PronunciationAddView(
                                word = wordInput,
                                isKeyboardOpen = isFormKeyboardOpen,
                                onDismissKeyboard = { activeField = null },
                                fieldState = FieldState(
                                    isActive = activeField == AddField.PRONUNCIATION,
                                    selection = activeSelection,
                                    onActivate = { offset ->
                                        activeField = AddField.PRONUNCIATION
                                        activeSelection = offset
                                    }
                                ),
                                onWordChange = { wordInput = it },
                                onCancel = onDismiss,
                                onAdd = {
                                    if (wordInput.isNotBlank()) {
                                        onAddCard(FlashcardTypes.PRONUNCIATION, wordInput, null, null, null, null)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                        AddCardTab.IMAGE -> {
                            ImageAddView(
                                word = wordInput,
                                isKeyboardOpen = isFormKeyboardOpen,
                                onDismissKeyboard = { activeField = null },
                                fieldState = FieldState(
                                    isActive = activeField == AddField.IMAGE,
                                    selection = activeSelection,
                                    onActivate = { offset ->
                                        activeField = AddField.IMAGE
                                        activeSelection = offset
                                    }
                                ),
                                onWordChange = { wordInput = it },
                                imageUri = selectedImageUri,
                                imageUrl = imageUrlInput,
                                onPickPhoto = {
                                    activeField = null
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        showImageCardCameraDialog = true
                                    } else {
                                        imageTabCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onCancel = onDismiss,
                                onAdd = {
                                    if (wordInput.isNotBlank()) {
                                        onAddCard(
                                            FlashcardTypes.IMAGE,
                                            wordInput,
                                            null,
                                            selectedImageUri?.toString() ?: imageUrlInput,
                                            null,
                                            null
                                        )
                                        onDismiss()
                                    }
                                }
                            )
                        }
                        AddCardTab.CONVERSATION -> {
                            ConversationCreationView(
                                messages = creationChatMessages,
                                isThinking = isCreationThinking,
                                maxFontSizeSp = maxFontSizeSp,
                                isKeyboardVisible = isConversationKeyboardVisible,
                                onKeyboardVisibleChange = { isConversationKeyboardVisible = it },
                                onSendMessage = onSendMessage,
                                onSendAudioMessage = onSendAudioMessage,
                                onApplyCorrection = onApplyCorrection,
                                onSelectLinguistic = {
                                    val lastTopic = deriveConversationCardTopic(
                                        messages = creationChatMessages,
                                        fallback = "Linguistic Element"
                                    )
                                    onAddCard(
                                        FlashcardTypes.CONVERSATION_LINGUISTIC,
                                        lastTopic.take(50),
                                        null,
                                        null,
                                        creationChatMessages,
                                        "Linguistic Scenario"
                                    )
                                    onDismiss()
                                },
                                onSelectTechnical = {
                                    val lastTopic = deriveConversationCardTopic(
                                        messages = creationChatMessages,
                                        fallback = "Technical Topic"
                                    )
                                    onAddCard(
                                        FlashcardTypes.CONVERSATION_TECHNICAL,
                                        lastTopic.take(50),
                                        null,
                                        null,
                                        creationChatMessages,
                                        "Technical Question"
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            // Full-width bottom pop-up keyboard overlaying Cancel / Add Card buttons
            val active = activeField
            val activeText = if (active == AddField.TEXT_BACK) backInput else wordInput
            CreationVirtualKeyboard(
                visible = active != null && selectedTab != AddCardTab.CONVERSATION,
                text = TextFieldValue(
                    activeText,
                    TextRange(activeSelection.coerceIn(0, activeText.length))
                ),
                onTextChange = { updated ->
                    val currentActive = activeField ?: return@CreationVirtualKeyboard
                    if (currentActive == AddField.TEXT_BACK) {
                        backInput = updated.text
                    } else {
                        wordInput = updated.text
                    }
                    activeSelection = updated.selection.end
                },
                onSend = { activeField = null },
                showInputField = false,
                sendLabel = "Done",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showImageCardCameraDialog) {
        CameraCaptureDialog(
            onPhotoCaptured = { bitmap ->
                showImageCardCameraDialog = false
                scope.launch {
                    val savedUri = saveBitmapToAppFiles(context, bitmap)
                    if (savedUri != null) {
                        selectedImageUri = savedUri
                        imageUrlInput = savedUri.toString()
                    }
                }
            },
            onGalleryPick = { uri ->
                showImageCardCameraDialog = false
                scope.launch {
                    val savedUri = saveUriToAppFiles(context, uri) ?: uri
                    selectedImageUri = savedUri
                    imageUrlInput = savedUri.toString()
                }
            },
            onDismiss = {
                showImageCardCameraDialog = false
            }
        )
    }
}

@Composable
fun TextAddView(
    front: String,
    back: String,
    isKeyboardOpen: Boolean,
    onDismissKeyboard: () -> Unit,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    frontState: FieldState,
    backState: FieldState,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    val keyboardClearance by animateDpAsState(
        targetValue = if (isKeyboardOpen) 140.dp else 0.dp,
        label = "text_keyboard_clearance"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissKeyboard() }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = keyboardClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            WordField(
                value = front,
                onValueChange = onFrontChange,
                fieldState = frontState,
                placeholder = "What you see first — a question, a term, a prompt.",
                testTag = "text_front_input",
                minLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            WordField(
                value = back,
                onValueChange = onBackChange,
                fieldState = backState,
                placeholder = "What you reveal after thinking — the answer or explanation.",
                testTag = "text_back_input",
                minLines = 3
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Initial Stage: 2 Minutes",
                color = AccentPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AddCardActionButtons(onCancel, onAdd, front.isNotBlank() && back.isNotBlank())
    }
}

@Composable
fun SpellingAddView(
    word: String,
    isKeyboardOpen: Boolean,
    onDismissKeyboard: () -> Unit,
    onWordChange: (String) -> Unit,
    fieldState: FieldState,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    val keyboardClearance by animateDpAsState(
        targetValue = if (isKeyboardOpen) 140.dp else 0.dp,
        label = "spelling_keyboard_clearance"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissKeyboard() }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = keyboardClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            WordField(
                value = word,
                onValueChange = onWordChange,
                fieldState = fieldState,
                placeholder = "Word (Front: Sound • Back: Spelling)",
                testTag = "spelling_word_input",
                minLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Initial Stage: 2 Minutes",
                color = AccentPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AddCardActionButtons(onCancel, onAdd, word.isNotBlank())
    }
}

@Composable
fun PronunciationAddView(
    word: String,
    isKeyboardOpen: Boolean,
    onDismissKeyboard: () -> Unit,
    onWordChange: (String) -> Unit,
    fieldState: FieldState,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    val keyboardClearance by animateDpAsState(
        targetValue = if (isKeyboardOpen) 140.dp else 0.dp,
        label = "pronunciation_keyboard_clearance"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissKeyboard() }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = keyboardClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            WordField(
                value = word,
                onValueChange = onWordChange,
                fieldState = fieldState,
                placeholder = "Word (Front: Text • Back: Pronunciation sound)",
                testTag = "pronunciation_word_input",
                minLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Initial Stage: 2 Minutes",
                color = AccentPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AddCardActionButtons(onCancel, onAdd, word.isNotBlank())
    }
}

@Composable
fun ImageAddView(
    word: String,
    isKeyboardOpen: Boolean,
    onDismissKeyboard: () -> Unit,
    onWordChange: (String) -> Unit,
    fieldState: FieldState,
    imageUri: Uri?,
    imageUrl: String?,
    onPickPhoto: () -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    val keyboardClearance by animateDpAsState(
        targetValue = if (isKeyboardOpen) 140.dp else 0.dp,
        label = "image_keyboard_clearance"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissKeyboard() }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = keyboardClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            // Box 1: Camera + Gallery Picker / Image Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceInput)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
                    .clickable { onPickPhoto() }
                    .testTag("image_card_picker_box"),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null || !imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri ?: imageUrl,
                        contentDescription = "Selected Flashcard Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Capture or Pick Image",
                            tint = PrimaryPurpleLight,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to capture or choose photo",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Box 2: Word text input
            WordField(
                value = word,
                onValueChange = onWordChange,
                fieldState = fieldState,
                placeholder = "Word to guess (e.g., Photosynthesis, Stethoscope)",
                testTag = "image_word_input",
                minLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Initial Stage: 2 Minutes",
                color = AccentPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AddCardActionButtons(
            onCancel = onCancel,
            onAdd = onAdd,
            canAdd = word.isNotBlank() && (imageUri != null || !imageUrl.isNullOrBlank())
        )
    }
}

@Composable
private fun AddCardActionButtons(
    onCancel: () -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceCard,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("cancel_add_card_button")
        ) {
            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Button(
            onClick = onAdd,
            enabled = canAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryPurple,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("add_card_button")
        ) {
            Text("Add Card", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

private data class PendingConversationImage(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap
)

@Composable
fun ConversationCreationView(
    messages: List<ChatMessage>,
    isThinking: Boolean,
    maxFontSizeSp: Int,
    isKeyboardVisible: Boolean,
    onKeyboardVisibleChange: (Boolean) -> Unit,
    onSendMessage: (text: String, imageBitmaps: List<Bitmap>) -> Unit,
    onSendAudioMessage: (audioBase64: String, imageBitmaps: List<Bitmap>) -> Unit,
    onApplyCorrection: (messageId: String, correctionId: String) -> Unit,
    onSelectLinguistic: () -> Unit = {},
    onSelectTechnical: () -> Unit = {},
    emptyLabel: String = "Tap the conversation to type, hold to speak, or add an image.",
    bottomActions: (@Composable (
        canSubmit: Boolean,
        onAddImageClick: () -> Unit,
        pendingImageCount: Int
    ) -> Unit)? = null
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isListening by remember { mutableStateOf(false) }
    var isMicrophoneHeld by remember { mutableStateOf(false) }
    var showConversationCameraDialog by remember { mutableStateOf(false) }
    val pendingImages = remember { mutableStateListOf<PendingConversationImage>() }

    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()
    val currentOnSendMessage by rememberUpdatedState(onSendMessage)
    val currentOnSendAudioMessage by rememberUpdatedState(onSendAudioMessage)
    val currentIsKeyboardVisible by rememberUpdatedState(isKeyboardVisible)
    val currentOnKeyboardVisibleChange by rememberUpdatedState(onKeyboardVisibleChange)
    val audioRecorder = remember(context) { com.example.audio.AudioRecorderManager(context) }

    val conversationCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showConversationCameraDialog = true
        } else {
            Toast.makeText(
                context,
                "Camera permission needed for photo capture",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openConversationImagePicker() {
        currentOnKeyboardVisibleChange(false)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showConversationCameraDialog = true
        } else {
            conversationCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    RecordingVibration(isRecording = isListening)

    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.stopRecording()
        }
    }

    fun sendMessage(text: String = inputText.text) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && pendingImages.isEmpty()) return
        val attachedBitmaps = pendingImages.map { it.bitmap }
        pendingImages.clear()
        currentOnSendMessage(trimmed, attachedBitmaps)
        inputText = TextFieldValue("")
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && isMicrophoneHeld) {
            if (audioRecorder.startRecording()) {
                isListening = true
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

    val canSubmit = messages.any { it.sender == "user" } && !isThinking

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ConversationMessagePages(
                    messages = messages,
                    maxFontSizeSp = maxFontSizeSp,
                    isBusy = isThinking,
                    busyLabel = "Thinking...",
                    emptyLabel = emptyLabel,
                    onApplyCorrection = onApplyCorrection,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 2.dp)
                        .pointerInput(isThinking, context) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pointerId = down.id
                                var wasConsumed = down.isConsumed
                                var isDragGesture = false
                                var recordingRequested = false

                                val holdJob = gestureScope.launch {
                                    delay(280L)
                                    if (!isDragGesture && !wasConsumed && !isThinking) {
                                        recordingRequested = true
                                        isMicrophoneHeld = true
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else if (audioRecorder.startRecording()) {
                                            isListening = true
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
                                    if (isListening) {
                                        isListening = false
                                        gestureScope.launch {
                                            val audioBase64 = withContext(Dispatchers.Default) {
                                                audioRecorder.stopRecording()
                                            }
                                            if (!audioBase64.isNullOrBlank()) {
                                                val attachedBitmaps = pendingImages.map { it.bitmap }
                                                pendingImages.clear()
                                                currentOnSendAudioMessage(audioBase64, attachedBitmaps)
                                            }
                                        }
                                    }
                                } else if (!isDragGesture) {
                                    if (currentIsKeyboardVisible) {
                                        currentOnKeyboardVisibleChange(false)
                                    } else if (!wasConsumed && !isThinking) {
                                        currentOnKeyboardVisibleChange(true)
                                    }
                                }
                            }
                        }
                )
            }

            if (pendingImages.isNotEmpty() && !isKeyboardVisible) {
                PendingConversationImagesStrip(
                    items = pendingImages,
                    canSend = !isThinking,
                    onRemove = { id -> pendingImages.removeAll { it.id == id } },
                    onAddMore = { openConversationImagePicker() },
                    onSendImages = { sendMessage(inputText.text) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            if (bottomActions != null) {
                bottomActions(canSubmit, ::openConversationImagePicker, pendingImages.size)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openConversationImagePicker() },
                        enabled = !isThinking,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("conversation_add_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add image",
                            tint = PrimaryPurpleLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pendingImages.isNotEmpty()) {
                                "Image (${pendingImages.size})"
                            } else {
                                "Add image"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onSelectLinguistic,
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("conversation_linguistic_button"),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 10.dp
                        )
                    ) {
                        Text(
                            "Linguistic scenario",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                    Button(
                        onClick = onSelectTechnical,
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("conversation_technical_button"),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 10.dp
                        )
                    ) {
                        Text(
                            "Technical Q&A",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Full-width bottom pop-up keyboard overlaying the bottom buttons
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            if (isKeyboardVisible && pendingImages.isNotEmpty()) {
                PendingConversationImagesStrip(
                    items = pendingImages,
                    canSend = !isThinking,
                    onRemove = { id -> pendingImages.removeAll { it.id == id } },
                    onAddMore = { openConversationImagePicker() },
                    onSendImages = {
                        sendMessage(inputText.text)
                        currentOnKeyboardVisibleChange(false)
                    },
                    modifier = Modifier
                        .background(SurfaceDark.copy(alpha = 0.96f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            CreationVirtualKeyboard(
                visible = isKeyboardVisible,
                text = inputText,
                onTextChange = { inputText = it },
                onSend = { sendMessage(it) }
            )
        }
    }

    if (showConversationCameraDialog) {
        CameraCaptureDialog(
            onPhotoCaptured = { bitmap ->
                showConversationCameraDialog = false
                pendingImages.add(PendingConversationImage(bitmap = bitmap))
            },
            onGalleryPick = { uri ->
                showConversationCameraDialog = false
                gestureScope.launch {
                    decodeBitmapFromUri(context, uri)?.let { bmp ->
                        pendingImages.add(PendingConversationImage(bitmap = bmp))
                    }
                }
            },
            onGalleryPickMultiple = { uris ->
                showConversationCameraDialog = false
                gestureScope.launch {
                    val decoded = uris.mapNotNull { uri ->
                        decodeBitmapFromUri(context, uri)
                    }
                    decoded.forEach { bmp ->
                        pendingImages.add(PendingConversationImage(bitmap = bmp))
                    }
                }
            },
            onDismiss = {
                showConversationCameraDialog = false
            }
        )
    }
}

@Composable
private fun PendingConversationImagesStrip(
    items: List<PendingConversationImage>,
    canSend: Boolean,
    onRemove: (String) -> Unit,
    onAddMore: () -> Unit,
    onSendImages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("conversation_pending_images_strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { pending ->
                val imageBitmap = remember(pending.id) { pending.bitmap.asImageBitmap() }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Pending image attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .clickable { onRemove(pending.id) }
                            .testTag("remove_pending_image_${pending.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceInput)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onAddMore() }
                    .testTag("conversation_add_more_images_box")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add more images",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Button(
            onClick = onSendImages,
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryPurple,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("conversation_send_images_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send images",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Send", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

private fun deriveConversationCardTopic(
    messages: List<ChatMessage>,
    fallback: String
): String {
    val userLine = messages.lastOrNull { it.sender == "user" && it.text.isNotBlank() }
        ?.text
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
    if (!userLine.isNullOrBlank()) return userLine

    val modelLine = messages.lastOrNull { it.sender == "model" && !it.isError && it.text.isNotBlank() }
        ?.text
        ?.let { TutorMessageFormatter.toPlainSpeakableText(it) }
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
    return modelLine?.takeIf { it.isNotBlank() } ?: fallback
}

private suspend fun decodeBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1600
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return@withContext null

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

        val maxSide = max(sampled.width, sampled.height)
        if (maxSide <= maxDimension) {
            sampled
        } else {
            val scale = maxDimension.toFloat() / maxSide.toFloat()
            val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
            val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun WordField(
    value: String,
    fieldState: FieldState,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceInput)
            .border(
                width = 1.dp,
                color = if (fieldState.isActive) PrimaryPurple else SurfaceCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .heightIn(min = if (minLines > 1) 96.dp else 46.dp)
            .virtualFieldContainer { fieldState.onActivate(value.length) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag)
    ) {
        VirtualTextField(
            text = value,
            selection = fieldState.selection,
            isActive = fieldState.isActive,
            onActivate = fieldState.onActivate,
            placeholder = placeholder,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = if (minLines > 1) 6 else 1,
            onPaste = { clip ->
                onValueChange(clip)
                fieldState.onActivate(clip.length)
            }
        )
    }
}


private suspend fun saveBitmapToAppFiles(context: Context, bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, "flashcard_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val file = File(imagesDir, "card_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

private suspend fun saveUriToAppFiles(context: Context, uri: Uri): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val imagesDir = File(context.filesDir, "flashcard_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val file = File(imagesDir, "card_img_${System.currentTimeMillis()}.jpg")
            inputStream.use { stream ->
                FileOutputStream(file).use { out ->
                    stream.copyTo(out)
                }
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }
