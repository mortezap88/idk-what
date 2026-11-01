package com.example.data

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SrsStage(
    val title: String,
    val shortName: String,
    val durationMillis: Long,
    val color: Color
) {
    TWO_MINUTES("2 Minutes", "2m", 2 * 60 * 1000L, Color(0xFFE5E5E5)),
    TWENTY_MINUTES("20 Minutes", "20m", 20 * 60 * 1000L, Color(0xFFD0D0D0)),
    TWO_HOURS("2 Hours", "2h", 2 * 60 * 60 * 1000L, Color(0xFFBBBBBB)),
    ONE_DAY("1 Day", "1d", 24 * 60 * 60 * 1000L, Color(0xFFA6A6A6)),
    THREE_DAYS("3 Days", "3d", 3 * 24 * 60 * 60 * 1000L, Color(0xFF919191)),
    ONE_WEEK("1 Week", "7d", 7 * 24 * 60 * 60 * 1000L, Color(0xFF7D7D7D)),
    TWO_WEEKS("2 Weeks", "14d", 14 * 24 * 60 * 60 * 1000L, Color(0xFF686868)),
    ONE_MONTH("1 Month", "30d", 30 * 24 * 60 * 60 * 1000L, Color(0xFF535353));

    companion object {
        fun fromIndex(index: Int): SrsStage {
            val safeIndex = index.coerceIn(0, entries.size - 1)
            return entries[safeIndex]
        }
    }
}

data class DayReviewCount(
    val dayLabel: String, // "Today", "Fri", "Sat", etc.
    val count: Int,
    val dateMillis: Long
)

object SpacedRepetitionUtils {
    private fun normalizeStageIndex(stageIndex: Int): Int {
        return stageIndex.coerceIn(0, SrsStage.entries.size - 1)
    }

    fun calculateNextReviewTime(currentStageIndex: Int): Long {
        val stage = SrsStage.fromIndex(currentStageIndex)
        return System.currentTimeMillis() + stage.durationMillis
    }

    fun calculateNextStage(currentStageIndex: Int, grade: ReviewGrade): Int {
        val safeIndex = normalizeStageIndex(currentStageIndex)
        return when (grade) {
            ReviewGrade.AGAIN -> 0
            ReviewGrade.HARD -> safeIndex
            ReviewGrade.GOOD -> (safeIndex + 1).coerceAtMost(SrsStage.entries.size - 1)
            ReviewGrade.EASY -> (safeIndex + 2).coerceAtMost(SrsStage.entries.size - 1)
        }
    }

    fun isCardDue(card: Flashcard, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return card.isActive && card.nextReviewTimeMillis <= nowMillis
    }

    fun formatRemainingDuration(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "now"
        val totalSeconds = (remainingMillis + 999L) / 1_000L
        if (totalSeconds < 60L) return "${totalSeconds}s"
        val totalMinutes = remainingMillis / 60_000L
        if (totalMinutes < 60L) {
            val secs = (remainingMillis % 60_000L) / 1_000L
            return if (totalMinutes < 5L && secs > 0L) {
                "${totalMinutes}m ${secs}s"
            } else {
                "${totalMinutes}m"
            }
        }
        val totalHours = remainingMillis / 3_600_000L
        if (totalHours < 24L) {
            val mins = (remainingMillis % 3_600_000L) / 60_000L
            return if (mins > 0L) "${totalHours}h ${mins}m" else "${totalHours}h"
        }
        val days = remainingMillis / 86_400_000L
        val hours = (remainingMillis % 86_400_000L) / 3_600_000L
        return if (days < 3L && hours > 0L) "${days}d ${hours}h" else "${days}d"
    }

    fun getUpcomingSixDays(cards: List<Flashcard>): List<DayReviewCount> {
        val activeCards = cards.filter { it.isActive }
        val calendar = Calendar.getInstance()
        // Reset to start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val result = mutableListOf<DayReviewCount>()

        for (i in 0 until 6) {
            val dayStart = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis

            val label = if (i == 0) "Today" else dayFormat.format(Date(dayStart))
            val count = if (i == 0) {
                // Today counts active cards due before end of today (including overdue)
                activeCards.count { it.nextReviewTimeMillis < dayEnd }
            } else {
                // Future days count active cards due between start and end of that day
                activeCards.count { it.nextReviewTimeMillis in dayStart until dayEnd }
            }

            result.add(DayReviewCount(dayLabel = label, count = count, dateMillis = dayStart))
        }

        return result
    }
}

enum class ReviewGrade {
    AGAIN,
    HARD,
    GOOD,
    EASY
}
