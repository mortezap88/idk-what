package com.example.notepad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notepad.data.model.NotepadTab
import com.example.notepad.ui.theme.CyanAccent
import com.example.notepad.ui.theme.OnCyanAccent
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextMuted
import com.example.notepad.ui.theme.TextOffWhite

/**
 * Horizontal notepad switcher shown above the notes.
 *
 * Drag the row to reach every notepad, tap to switch, hold to rename or delete, and the
 * "+" stays pinned at the right edge so a new notepad is always one tap away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotepadTopBar(
    tabs: List<NotepadTab>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onLongPressTab: (Int, NotepadTab) -> Unit,
    onAddTabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .testTag("notepad_selector_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedTabIndex
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) CyanAccent else SquircleBg,
                    border = BorderStroke(
                        1.dp,
                        if (selected) CyanAccent else SquircleBorder
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(index) },
                            onLongClick = { onLongPressTab(index, tab) }
                        )
                        .testTag("tab_${tab.title}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.title,
                            color = if (selected) OnCyanAccent else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onAddTabClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("add_general_notepad_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New notepad",
                tint = TextOffWhite
            )
        }
    }
}
