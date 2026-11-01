package com.example

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Flashcard
import com.example.data.FlashcardStatus
import com.example.data.FlashcardTypes
import com.example.data.QuestionBankExporter
import com.example.data.Subject
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestionBankExportTest {

    @Test
    fun questionBankExporter_formatsNewestListAsQuestionsAreAddedModifiedOrDeleted() {
        val subject = Subject(id = 42L, name = "Molecular Biology")
        val q1 = Flashcard(
            id = 1L,
            type = FlashcardTypes.EXAM,
            word = "What enzyme unwinds the DNA double helix during replication?",
            backText = "DNA helicase breaks hydrogen bonds between complementary base pairs.",
            subjectId = 42L,
            stageIndex = 0,
            createdAtMillis = 1000L,
            status = FlashcardStatus.ACTIVE
        )
        val otherSubjectCard = Flashcard(
            id = 2L,
            type = FlashcardTypes.EXAM,
            word = "Unrelated physics question",
            backText = "Newton's second law",
            subjectId = 99L,
            createdAtMillis = 1500L
        )

        val initialBank = QuestionBankExporter.getSubjectQuestionBank(listOf(q1, otherSubjectCard), 42L)
        assertEquals(1, initialBank.size)
        val firstExport = QuestionBankExporter.formatSubjectQuestionBank(subject, initialBank)
        assertTrue(firstExport.contains("# Question Bank: Molecular Biology"))
        assertTrue(firstExport.contains("Total Questions: 1 (Active: 1, Learned: 0, Dumb: 0)"))
        assertTrue(firstExport.contains("DNA helicase"))
        assertFalse(firstExport.contains("Unrelated physics question"))

        // Simulate adding a second generated question and marking the first as DUMB
        val q1Dumb = q1.copy(status = FlashcardStatus.DUMB)
        val q2 = Flashcard(
            id = 3L,
            type = FlashcardTypes.EXAM,
            word = "Why is DNA synthesis continuous on the leading strand?",
            backText = "DNA polymerase III synthesizes exclusively in the 5' to 3' direction toward the replication fork.",
            subjectId = 42L,
            stageIndex = 2,
            createdAtMillis = 2000L,
            status = FlashcardStatus.ACTIVE
        )
        val updatedBank = QuestionBankExporter.getSubjectQuestionBank(
            listOf(q1Dumb, otherSubjectCard, q2),
            42L
        )
        val secondExport = QuestionBankExporter.formatSubjectQuestionBank(subject, updatedBank)
        assertTrue(secondExport.contains("Total Questions: 2 (Active: 1, Learned: 0, Dumb: 1)"))
        assertTrue(secondExport.contains("1. [DUMB |"))
        assertTrue(secondExport.contains("2. [ACTIVE |"))
        assertTrue(secondExport.contains("Why is DNA synthesis continuous on the leading strand?"))
    }

    @Test
    fun copySubjectQuestionBank_alwaysCopiesNewestDatabaseStateToClipboard() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).flashcardDao()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val subjectId = dao.insertSubject(Subject(name = "Operating Systems"))
        val card1Id = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What is thrashing in virtual memory?",
                backText = "Excessive paging activity when working sets exceed physical frames.",
                subjectId = subjectId,
                createdAtMillis = 10_000L
            )
        )

        val vm = MainViewModel(app)
        assertEquals(1, vm.getLatestSubjectQuestionBank(subjectId).size)

        vm.copySubjectQuestionBank(subjectId)
        repeat(30) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (clipboard.primaryClip?.getItemAt(0)?.text?.contains("thrashing") == true) return@repeat
            delay(30L)
        }
        val firstClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(firstClip.contains("Total Questions: 1"))
        assertTrue(firstClip.contains("What is thrashing in virtual memory?"))

        // Mutate question bank: mark first card LEARNED + insert a brand-new question
        dao.updateCardStatus(card1Id, FlashcardStatus.LEARNED)
        dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "How does a TLB reduce address translation latency?",
                backText = "Caches recent virtual-to-physical page table translations in fast associative hardware.",
                subjectId = subjectId,
                createdAtMillis = 20_000L
            )
        )

        assertEquals(2, vm.getLatestSubjectQuestionBank(subjectId).size)

        vm.copySubjectQuestionBank(subjectId)
        repeat(30) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (clipboard.primaryClip?.getItemAt(0)?.text?.contains("TLB") == true) return@repeat
            delay(30L)
        }
        val secondClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(secondClip.contains("Total Questions: 2 (Active: 1, Learned: 1, Dumb: 0)"))
        assertTrue(secondClip.contains("How does a TLB reduce address translation latency?"))
    }

    @Test
    fun deleteSubjectQuestionBank_removesAllStatusesForSubjectWhileKeepingSubjectAndOtherCards() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).flashcardDao()

        val targetSubjectId = dao.insertSubject(
            Subject(
                name = "Computer Networks",
                textbook = "TCP uses three-way handshake and sliding window flow control.",
                extraKnowledge = "RFC 793 background notes."
            )
        )
        val otherSubjectId = dao.insertSubject(Subject(name = "Linear Algebra"))

        dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "Explain the TCP three-way handshake.",
                backText = "SYN, SYN-ACK, ACK.",
                subjectId = targetSubjectId,
                status = FlashcardStatus.ACTIVE
            )
        )
        dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What is sliding window flow control?",
                backText = "Receiver advertises rwnd to bound unacknowledged bytes.",
                subjectId = targetSubjectId,
                status = FlashcardStatus.LEARNED
            )
        )
        dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "Trivial port trivia",
                backText = "Port 80",
                subjectId = targetSubjectId,
                status = FlashcardStatus.DUMB
            )
        )
        dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "Define eigenvalues of a square matrix.",
                backText = "Scalars lambda satisfying Av = lambda v for nonzero v.",
                subjectId = otherSubjectId,
                status = FlashcardStatus.ACTIVE
            )
        )

        val vm = MainViewModel(app)
        assertEquals(3, vm.getLatestSubjectQuestionBank(targetSubjectId).size)
        assertEquals(1, vm.getLatestSubjectQuestionBank(otherSubjectId).size)

        vm.deleteSubjectQuestionBank(targetSubjectId)
        repeat(30) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (dao.getCardsBySubjectSnapshot(targetSubjectId).isEmpty()) return@repeat
            delay(30L)
        }

        assertEquals(0, vm.getLatestSubjectQuestionBank(targetSubjectId).size)
        assertEquals(1, vm.getLatestSubjectQuestionBank(otherSubjectId).size)

        val preservedSubject = dao.getSubjectById(targetSubjectId)
        assertTrue(preservedSubject != null)
        assertEquals(
            "TCP uses three-way handshake and sliding window flow control.",
            preservedSubject?.textbook
        )
    }
}
