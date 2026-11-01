package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.CorrectionType
import com.example.network.LinguisticCorrection
import com.example.ui.theme.CorrectionRedBg
import com.example.ui.theme.CorrectionRedText
import com.example.ui.theme.ImprovementYellowBg
import com.example.ui.theme.ImprovementYellowText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
private fun CorrectionDialog(
    correction: LinguisticCorrection,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val isGrammar = correction.type == CorrectionType.GRAMMAR
    val badgeText = if (isGrammar) "Correction" else "Improvement"
    val badgeColor = if (isGrammar) CorrectionRedText else ImprovementYellowText
    val badgeBg = if (isGrammar) CorrectionRedBg else ImprovementYellowBg

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Suggested:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    BidiText(
                        text = correction.suggested.ifBlank {
                            "Remove “${correction.original}”"
                        },
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                }
                if (correction.reason.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Reason:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        BidiText(
                            text = correction.reason,
                            fontSize = 12.5.sp,
                            color = TextPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("apply_correction_button")
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Apply Fix", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ignore_correction_button")
            ) {
                Text("Ignore", color = TextSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ConversationCorrectionDialog(
    correction: LinguisticCorrection,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) = CorrectionDialog(correction, onApply, onDismiss)
