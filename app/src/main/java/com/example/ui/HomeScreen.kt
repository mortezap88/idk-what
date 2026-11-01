package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Flashcard
import com.example.data.SpacedRepetitionUtils
import com.example.data.SrsStage
import com.example.ui.theme.BadgeGreenBg
import com.example.ui.theme.BadgeGreenBorder
import com.example.ui.theme.BadgeGreenText
import com.example.ui.theme.BgDark
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
internal fun rememberCurrentTimeMillis(tickIntervalMs: Long = 1_000L): Long {
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), key1 = tickIntervalMs) {
        while (true) {
            delay(tickIntervalMs)
            value = System.currentTimeMillis()
        }
    }
    return nowMillis
}

@Composable
fun HomeScreen(
    cards: List<Flashcard>,
    onOpenAdd: () -> Unit,
    onOpenReview: (stageIndex: Int?) -> Unit
) {
    val nowMillis = rememberCurrentTimeMillis()
    val upcomingDays by remember(cards, nowMillis / 60_000L) {
        derivedStateOf { SpacedRepetitionUtils.getUpcomingSixDays(cards) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAdd,
                containerColor = PrimaryPurple,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("add_flashcard_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Flashcard",
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
            // Six-day forecast, without its own card shell.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    upcomingDays.forEachIndexed { index, day ->
                        DayColumn(
                            dayName = day.dayLabel,
                            count = day.count,
                            isToday = index == 0
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Spaced Repetition Stages",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 17.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            itemsIndexed(SrsStage.entries) { index, stage ->
                val stageCards = cards.filter { it.isActive && it.stageIndex == index }
                val dueInStage = stageCards.count { SpacedRepetitionUtils.isCardDue(it, nowMillis) }
                val totalInStage = stageCards.size
                val nextDueInLabel = if (dueInStage == 0 && totalInStage > 0) {
                    stageCards.minOfOrNull { it.nextReviewTimeMillis }?.let { earliest ->
                        SpacedRepetitionUtils.formatRemainingDuration(earliest - nowMillis)
                    }
                } else {
                    null
                }

                StageRowItem(
                    stage = stage,
                    stageIndex = index,
                    dueCount = dueInStage,
                    totalCount = totalInStage,
                    nextDueInLabel = nextDueInLabel,
                    onClick = {
                        if (dueInStage > 0) {
                            onOpenReview(index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DayColumn(
    dayName: String,
    count: Int,
    isToday: Boolean
) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isToday) SurfaceCard else SurfaceInput)
            .border(
                1.dp,
                if (isToday) PrimaryPurple.copy(alpha = 0.5f) else SurfaceCardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isToday) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (count > 0) TextPrimary else TextMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun StageRowItem(
    stage: SrsStage,
    stageIndex: Int,
    dueCount: Int,
    totalCount: Int,
    nextDueInLabel: String? = null,
    onClick: () -> Unit
) {
    val isAccessible = dueCount > 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isAccessible) SurfaceCard else SurfaceCard.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = if (isAccessible) PrimaryPurple.copy(alpha = 0.45f) else SurfaceCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .then(
                if (isAccessible) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp)
            .testTag("stage_row_$stageIndex"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stage.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = when {
                        isAccessible -> PrimaryPurpleLight
                        totalCount > 0 -> TextSecondary
                        else -> TextMuted
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            )

            if (dueCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BadgeGreenBg)
                        .border(1.dp, BadgeGreenBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$dueCount due",
                        color = BadgeGreenText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else if (totalCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Scheduled, not due yet",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (nextDueInLabel != null) {
                            "$totalCount · in $nextDueInLabel"
                        } else {
                            "$totalCount scheduled"
                        },
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
