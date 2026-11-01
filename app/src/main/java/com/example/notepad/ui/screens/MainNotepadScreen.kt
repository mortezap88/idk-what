package com.example.notepad.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.CreationVirtualKeyboard
import com.example.ui.KeyboardDismissSurface
import com.example.ui.RecordingVibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.notepad.data.model.MediaType
import com.example.notepad.ui.components.AudioDetailsDialog
import com.example.notepad.ui.components.CameraCaptureDialog
import com.example.notepad.ui.components.DeleteActionPopup
import com.example.notepad.ui.components.ImagePreviewDialog
import com.example.notepad.ui.components.NotepadCard
import com.example.notepad.ui.components.NotepadTopBar
import com.example.notepad.ui.components.SettingsSidePanel
import com.example.notepad.ui.theme.CyanAccent
import com.example.notepad.ui.theme.DeepObsidianBg
import com.example.notepad.ui.theme.NotepadCardBg
import com.example.notepad.ui.theme.OnCyanAccent
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextMuted
import com.example.notepad.ui.theme.TextOffWhite
import com.example.notepad.ui.viewmodel.NotepadViewModel

private data class ActiveLineState(
    val blockId: String,
    val lineId: String,
    val selection: Int,
    val pendingText: String? = null
)

private enum class NotepadSidePanel {
    SETTINGS
}

@Composable
fun MainNotepadScreen(
    viewModel: NotepadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tabs by viewModel.tabs.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val currentlyPlayingId by viewModel.currentlyPlayingId.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val previewImageItem by viewModel.previewImageItem.collectAsState()
    val selectedAudioItem by viewModel.selectedAudioItem.collectAsState()
    val itemToDelete by viewModel.itemToDelete.collectAsState()
    val showNewTabDialog by viewModel.showNewTabDialog.collectAsState()
    val tabActionTarget by viewModel.tabActionTarget.collectAsState()

    val avalaiApiKey by viewModel.avalaiApiKey.collectAsState()

    var activeMediaBlockId by remember { mutableStateOf<String?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var isRenamingTargetTab by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var sidePanel by remember { mutableStateOf<NotepadSidePanel?>(null) }
    var activeLine by remember { mutableStateOf<ActiveLineState?>(null) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraDialog = true
        } else {
            activeMediaBlockId?.let(viewModel::resumeEmptyBlockCleanup)
            Toast.makeText(context, "Camera permission needed for photo capture", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for voice recording
    var pendingRecordBlockId by remember { mutableStateOf<String?>(null) }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingRecordBlockId?.let { blockId ->
                viewModel.startVoiceRecording(blockId)
            }
        } else {
            pendingRecordBlockId?.let(viewModel::resumeEmptyBlockCleanup)
            Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val currentTab = tabs.getOrNull(selectedTabIndex)

    val activeResolvedLine = activeLine?.let { state ->
        currentTab?.blocks
            ?.firstOrNull { it.id == state.blockId }
            ?.lines
            ?.firstOrNull { it.id == state.lineId }
    }
    val activeLineText = activeResolvedLine?.text ?: activeLine?.pendingText

    LaunchedEffect(activeResolvedLine?.id, activeLine?.lineId) {
        val state = activeLine ?: return@LaunchedEffect
        if (state.pendingText != null && activeResolvedLine?.id == state.lineId) {
            activeLine = state.copy(pendingText = null)
        } else if (state.pendingText == null && activeResolvedLine == null) {
            activeLine = null
        }
    }

    fun handleEnterOnActiveLine(state: ActiveLineState, currentText: String) {
        val clampedSelection = state.selection.coerceIn(0, currentText.length)
        val beforeCursor = currentText.substring(0, clampedSelection)
        val afterCursor = currentText.substring(clampedSelection)

        val block = currentTab?.blocks?.firstOrNull { it.id == state.blockId }
        val currentIndex = block?.lines?.indexOfFirst { it.id == state.lineId } ?: -1
        val nextLine = if (block != null && currentIndex >= 0) {
            block.lines.getOrNull(currentIndex + 1)
        } else {
            null
        }

        if (afterCursor.isEmpty() && nextLine != null && nextLine.text.isEmpty()) {
            if (beforeCursor != currentText) {
                viewModel.updateLine(state.blockId, state.lineId, beforeCursor)
            }
            activeLine = ActiveLineState(
                blockId = state.blockId,
                lineId = nextLine.id,
                selection = 0
            )
        } else {
            val newLineId = viewModel.splitLineOnEnter(
                blockId = state.blockId,
                currentLineId = state.lineId,
                beforeCursor = beforeCursor,
                afterCursor = afterCursor
            )
            activeLine = ActiveLineState(
                blockId = state.blockId,
                lineId = newLineId,
                selection = 0,
                pendingText = afterCursor
            )
        }
    }

    LaunchedEffect(selectedTabIndex) { activeLine = null }
    LaunchedEffect(sidePanel) {
        if (sidePanel != null) {
            activeLine = null
            viewModel.refreshAiSettings()
        }
    }

    // Gentle repeating buzz while a memo is being captured.
    RecordingVibration(isRecording = isRecording)

    BackHandler(enabled = sidePanel != null) {
        sidePanel = null
    }
    BackHandler(enabled = activeLine != null) {
        activeLine = null
    }

    KeyboardDismissSurface(
        isKeyboardVisible = activeLine != null,
        onDismissKeyboard = { activeLine = null },
        modifier = modifier.fillMaxSize()
    ) {
    Scaffold(
        containerColor = DeepObsidianBg,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            val activeState = activeLine
            if (activeState != null && activeLineText != null) {
                CreationVirtualKeyboard(
                    text = TextFieldValue(
                        activeLineText,
                        TextRange(activeState.selection.coerceIn(0, activeLineText.length))
                    ),
                    onTextChange = { updated ->
                        if (updated.text.contains('\n')) {
                            val parts = updated.text.split('\n', limit = 2)
                            val afterNewline = parts.getOrElse(1) { "" }
                            val newLineId = viewModel.splitLineOnEnter(
                                blockId = activeState.blockId,
                                currentLineId = activeState.lineId,
                                beforeCursor = parts[0],
                                afterCursor = afterNewline
                            )
                            activeLine = ActiveLineState(
                                blockId = activeState.blockId,
                                lineId = newLineId,
                                selection = 0,
                                pendingText = afterNewline
                            )
                        } else {
                            viewModel.updateLine(activeState.blockId, activeState.lineId, updated.text)
                            activeLine = activeState.copy(
                                selection = updated.selection.end,
                                pendingText = if (activeResolvedLine == null) updated.text else null
                            )
                        }
                    },
                    onSend = {
                        handleEnterOnActiveLine(activeState, activeLineText)
                    },
                    showInputField = false,
                    sendLabel = "Enter",
                    useSpecialKeyStyleForSend = true
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(sidePanel, isRecording) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        // Inside the tabbed shell only edge swipes open the panels, so that a
                        // normal horizontal swipe still changes the app page.
                        val edgeZone = 32.dp.toPx()
                        val startedAtEdge = down.position.x <= edgeZone ||
                            down.position.x >= size.width - edgeZone
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            val delta = change.position - down.position
                            val horizontalDistance = kotlin.math.abs(delta.x)
                            val verticalDistance = kotlin.math.abs(delta.y)
                            val swipeThreshold = 44.dp.toPx()

                            if ((sidePanel != null || startedAtEdge) &&
                                horizontalDistance > swipeThreshold &&
                                horizontalDistance > verticalDistance * 1.25f
                            ) {
                                val targetPanel = when {
                                    sidePanel == null && delta.x < 0f -> NotepadSidePanel.SETTINGS
                                    sidePanel == NotepadSidePanel.SETTINGS && delta.x > 0f -> null
                                    else -> sidePanel
                                }
                                if (targetPanel != sidePanel) {
                                    sidePanel = targetPanel
                                    change.consume()
                                }
                                break
                            }
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NotepadTopBar(
                    tabs = tabs,
                    selectedTabIndex = selectedTabIndex,
                    onSelectTab = { viewModel.selectTab(it) },
                    onLongPressTab = { index, tab -> viewModel.openTabActionMenu(index, tab) },
                    onAddTabClick = { viewModel.openNewTabDialog() }
                )

                // List of sub-notepad blocks
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (currentTab != null) {
                        items(currentTab.blocks, key = { it.id }) { block ->
                            NotepadCard(
                                block = block,
                                currentlyPlayingId = currentlyPlayingId,
                                onLineTypingChanged = { lineId, isTyping ->
                                    viewModel.setLineTyping(block.id, lineId, isTyping)
                                },
                                onUpdateLine = { lineId, text ->
                                    viewModel.updateLine(block.id, lineId, text)
                                },
                                onLineActivated = { blockId, lineId, offset ->
                                    activeLine = ActiveLineState(blockId, lineId, offset)
                                },
                                activeLineId = activeLine?.lineId,
                                activeSelection = activeLine?.selection ?: 0,
                                onInsertLineAfter = { currentLineId, text ->
                                    viewModel.insertLineAfter(block.id, currentLineId, text)
                                },
                                onRemoveLine = { lineId ->
                                    viewModel.removeLine(block.id, lineId)
                                },
                                onTapMediaItem = { item ->
                                    if (item.type == MediaType.IMAGE) {
                                        viewModel.showImagePreview(item)
                                    } else {
                                        viewModel.showAudioDetails(item)
                                    }
                                },
                                onLongPressMediaItem = { item ->
                                    viewModel.requestDeleteItem(block.id, item)
                                },
                                onTapAddMedia = {
                                    activeMediaBlockId = block.id
                                    viewModel.keepBlock(block.id)
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        showCameraDialog = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onStartVoiceRecording = {
                                    viewModel.keepBlock(block.id)
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.startVoiceRecording(block.id)
                                    } else {
                                        pendingRecordBlockId = block.id
                                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onStopVoiceRecording = {
                                    viewModel.stopVoiceRecordingAndTranscribe(block.id)
                                },
                                onCancelVoiceRecording = {
                                    viewModel.cancelVoiceRecording()
                                }
                            )
                        }

                        // Boxless, pure white '+' button below the card
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.addSubNotepad() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("add_sub_notepad_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add sub notepad",
                                        tint = TextOffWhite,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = sidePanel != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { sidePanel = null }
                )
            }

            AnimatedVisibility(
                visible = sidePanel == NotepadSidePanel.SETTINGS,
                enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) +
                    fadeIn(tween(180)),
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) +
                    fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.9f)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.86f)
                    .zIndex(2f)
            ) {
                SettingsSidePanel(
                    initialAvalaiKey = avalaiApiKey,
                    onSaveAiSettings = { avalaiKey ->
                        viewModel.updateAiSettings(avalaiKey)
                    },
                    onResetTemplateData = {
                        viewModel.resetToDefaultData()
                        sidePanel = null
                    },
                    onDismiss = { sidePanel = null }
                )
            }

            // Recording feedback is haptic only; the held button flashes its own frame.

            // Camera Photo Capture Modal (matching Screenshot 2)
            if (showCameraDialog) {
                CameraCaptureDialog(
                    onPhotoCaptured = { bitmap ->
                        activeMediaBlockId?.let { blockId ->
                            viewModel.addImageBitmap(blockId, bitmap)
                        }
                        showCameraDialog = false
                    },
                    onGalleryPick = { uri ->
                        activeMediaBlockId?.let { blockId ->
                            viewModel.addImageUri(blockId, uri)
                        }
                        showCameraDialog = false
                    },
                    onGalleryPickMultiple = { uris ->
                        activeMediaBlockId?.let { blockId ->
                            viewModel.addImageUris(blockId, uris)
                        }
                        showCameraDialog = false
                    },
                    onDismiss = {
                        showCameraDialog = false
                        activeMediaBlockId?.let(viewModel::resumeEmptyBlockCleanup)
                    }
                )
            }

            // Image Preview Dialog (smooth enlargement)
            previewImageItem?.let { item ->
                ImagePreviewDialog(
                    item = item,
                    onDismiss = { viewModel.dismissImagePreview() },
                    onDelete = {
                        val blockId = currentTab?.blocks?.firstOrNull { it.mediaItems.any { m -> m.id == item.id } }?.id
                        if (blockId != null) {
                            viewModel.requestDeleteItem(blockId, item)
                        }
                        viewModel.dismissImagePreview()
                    }
                )
            }

            // Voice Note Review Modal (matching Screenshot 1)
            selectedAudioItem?.let { item ->
                val owningBlockId = currentTab?.blocks?.firstOrNull {
                    it.mediaItems.any { m -> m.id == item.id }
                }?.id ?: tabs.asSequence()
                    .flatMap { it.blocks.asSequence() }
                    .firstOrNull { it.mediaItems.any { m -> m.id == item.id } }
                    ?.id

                AudioDetailsDialog(
                    item = item,
                    isPlaying = currentlyPlayingId == item.id,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    onTogglePlay = { viewModel.toggleActiveAudioPlayback(item) },
                    onDelete = {
                        if (owningBlockId != null) {
                            viewModel.requestDeleteItem(owningBlockId, item)
                        }
                        viewModel.dismissAudioDetails()
                    },
                    onRetry = {
                        if (owningBlockId != null) {
                            viewModel.retryAudioTranscription(owningBlockId, item.id)
                        }
                    },
                    onApplyCorrection = { correctionId ->
                        if (owningBlockId != null) {
                            viewModel.applyAudioCorrection(owningBlockId, item.id, correctionId)
                        }
                    },
                    onDismiss = { viewModel.dismissAudioDetails() }
                )
            }

            // Stretched square delete popup
            itemToDelete?.let { pair ->
                DeleteActionPopup(
                    targetItem = pair.second,
                    onConfirmDelete = { viewModel.confirmDeleteItem() },
                    onDismiss = { viewModel.dismissDeletePopup() }
                )
            }

            // Long Press on Notepad Tab Action Dialog (Rename or Delete)
            tabActionTarget?.let { pair ->
                val tabIndex = pair.first
                val targetTab = pair.second

                if (!isRenamingTargetTab) {
                    AlertDialog(
                        onDismissRequest = { viewModel.closeTabActionMenu() },
                        containerColor = NotepadCardBg,
                        title = {
                            Text(
                                text = "Notepad: ${targetTab.title}",
                                color = TextOffWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Choose an action for this notepad:",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        renameInputText = targetTab.title
                                        isRenamingTargetTab = true
                                    },
                                    modifier = Modifier.testTag("rename_tab_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename", color = CyanAccent)
                                }

                                if (tabs.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteTab(tabIndex)
                                        },
                                        modifier = Modifier.testTag("delete_tab_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", color = Color(0xFFFF5252))
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.closeTabActionMenu() }) {
                                Text("Cancel", color = TextMuted)
                            }
                        }
                    )
                } else {
                    // Rename prompt dialog
                    AlertDialog(
                        onDismissRequest = {
                            isRenamingTargetTab = false
                            viewModel.closeTabActionMenu()
                        },
                        containerColor = NotepadCardBg,
                        title = {
                            Text(
                                text = "Rename Notepad",
                                color = TextOffWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = renameInputText,
                                onValueChange = { renameInputText = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextOffWhite,
                                    unfocusedTextColor = TextOffWhite,
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = SquircleBorder
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("rename_tab_input")
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.renameTab(tabIndex, renameInputText)
                                    isRenamingTargetTab = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Save", color = OnCyanAccent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    isRenamingTargetTab = false
                                    viewModel.closeTabActionMenu()
                                }
                            ) {
                                Text("Cancel", color = TextMuted)
                            }
                        }
                    )
                }
            }
        }
    }
    }

    // Dialog for adding a new general notepad tab
    if (showNewTabDialog) {
        var tabTitleInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.closeNewTabDialog() },
            containerColor = NotepadCardBg,
            title = {
                Text(
                    text = "New Notepad",
                    color = TextOffWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this general notepad tab:",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tabTitleInput,
                        onValueChange = { tabTitleInput = it },
                        singleLine = true,
                        placeholder = { Text("Notepad name", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = SquircleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_notepad_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createNewTab(tabTitleInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("confirm_create_notepad_button")
                ) {
                    Text("Create", color = OnCyanAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeNewTabDialog() }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

