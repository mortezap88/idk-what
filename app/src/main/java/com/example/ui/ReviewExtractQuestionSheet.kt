package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.network.ChatMessage
import com.example.ui.theme.EvalGreenDot
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Full-screen window opened when tapping "Extract Question" during Review.
 *
 * It begins with an opening question asking what part of the review item the learner wants to add,
 * carries full context of the review conversation + Textbook + Extra Knowledge, and lets the
 * learner tap "Generate the question" -> inspect Question & Answer -> "Save" (accept) or go back
 * to refine the conversation.
 */
@Composable
fun ReviewExtractQuestionSheet(
    subjectName: String?,
    hasTextbook: Boolean,
    hasExtraKnowledge: Boolean,
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

    val contextSubtitle = buildString {
        if (!subjectName.isNullOrBlank()) {
            append(subjectName)
            append(" · ")
        }
        append("Review chat")
        if (hasTextbook) append(" · Textbook")
        if (hasExtraKnowledge) append(" · Extra knowledge")
    }

    Surface(
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
            .testTag("review_extract_question_sheet")
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
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("review_extract_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to review",
                                tint = TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.review_extract_question_header),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            BidiText(
                                text = contextSubtitle,
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
                            emptyLabel = "Tap to type, hold to speak, or add an image about what caught your interest in this review.",
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
                                            .testTag("review_extract_add_image_button")
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
                                            .testTag("review_extract_generate_button")
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
                    subjectName = subjectName ?: "Review Questions",
                    draftState = draftState,
                    onBackToChat = onDismissDraft,
                    onRegenerate = onGenerateQuestion,
                    onSave = onAcceptDraft,
                    generatingSubtitle = stringResource(R.string.review_extract_generating_subtitle)
                )
            }
        }
    }
}
