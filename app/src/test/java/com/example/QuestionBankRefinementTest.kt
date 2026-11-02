package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Flashcard
import com.example.data.FlashcardStatus
import com.example.data.FlashcardTypes
import com.example.data.QuestionBankRefinementParser
import com.example.data.QuestionBankRefinementParser.RefinementOperation
import com.example.data.Subject
import com.example.network.GeminiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestionBankRefinementTest {

    @Test
    fun incrementalCollector_streamsEditCombineAndAddWhileFilteringDuplicatesAndInvalidIds() {
        val emitted = mutableListOf<RefinementOperation>()
        val collector = QuestionBankRefinementParser.IncrementalRefinementCollector(
            activeCardIds = setOf(10L, 11L, 12L),
            existingQuestions = listOf(
                "What does it do?",
                "Part A of mitosis?",
                "Part B of mitosis?",
                "Already learned question?"
            ),
            onOperation = { emitted.add(it) }
        )

        val streamChunk1 = """
            [op_item]
            [action] EDIT [/action]
            [ids] 10 [/ids]
            [q] What is the primary physiological role of surfactant in pulmonary alveoli? [/q]
            [a] It lowers alveolar surface tension and prevents alveolar collapse during expiration. [/a]
            [/op_item]
            [op_item]
            [action] COMBINE [/action]
            [ids] 11, 12 [/ids]
        """.trimIndent()

        collector.onPartialStream(streamChunk1)
        assertEquals(1, emitted.size)
        assertTrue(emitted[0] is RefinementOperation.Edit)

        val fullStream = streamChunk1 + "\n" + """
            [q] How do prophase and metaphase transition chromosomes toward equatorial alignment in mitosis? [/q]
            [a] Chromatin condenses and spindle fibers attach to kinetochores before aligning chromosomes along the metaphase plate. [/a]
            [/op_item]
            [op_item]
            [action] EDIT [/action]
            [ids] 999 [/ids]
            [q] Should be ignored because ID 999 is not an active card [/q]
            [a] Ignored answer [/a]
            [/op_item]
            [op_item]
            [action] ADD [/action]
            [q] Already learned question? [/q]
            [a] Duplicate of learned card — should be skipped [/a]
            [/op_item]
            [op_item]
            [action] ADD [/action]
            [q] At what gestational week do type II pneumocytes begin secreting surfactant in the textbook? [/q]
            [a] Around week 24 of gestation, reaching functional maturity by weeks 34–35.
            This specific timeline is frequently tested on exams. [/a]
        """.trimIndent() // Intentionally missing trailing [/op_item] to verify end-of-stream salvage

        collector.finish(fullStream)

        assertEquals(3, collector.emittedOperations.size)
        val editOp = collector.emittedOperations[0] as RefinementOperation.Edit
        assertEquals(10L, editOp.targetId)
        assertTrue(editOp.question.contains("surfactant"))

        val combineOp = collector.emittedOperations[1] as RefinementOperation.Combine
        assertEquals(listOf(11L, 12L), combineOp.sourceIds)
        assertTrue(combineOp.question.contains("prophase and metaphase"))

        val addOp = collector.emittedOperations[2] as RefinementOperation.Add
        assertTrue(addOp.question.contains("gestational week"))
        assertTrue(addOp.answer.contains("week 24"))
    }

    @Test
    fun applyOperationToDatabase_updatesCombinesAndAddsWhileProtectingLearnedDumbAndOtherSubjects() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).flashcardDao()

        val subjectId = dao.insertSubject(Subject(name = "Renal Physiology"))
        val otherSubjectId = dao.insertSubject(Subject(name = "Cardiology"))

        val flawedActiveId = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "Why filter?",
                backText = "Because kidney.",
                subjectId = subjectId,
                stageIndex = 2,
                nextReviewTimeMillis = 50_000L,
                createdAtMillis = 1_000L,
                status = FlashcardStatus.ACTIVE
            )
        )
        val frag1Id = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What does aldosterone do to Na+?",
                backText = "Increases Na+ reabsorption.",
                subjectId = subjectId,
                stageIndex = 3,
                nextReviewTimeMillis = 80_000L,
                createdAtMillis = 2_000L,
                status = FlashcardStatus.ACTIVE
            )
        )
        val frag2Id = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What does aldosterone do to K+?",
                backText = "Increases K+ secretion.",
                subjectId = subjectId,
                stageIndex = 1,
                nextReviewTimeMillis = 30_000L,
                createdAtMillis = 3_000L,
                status = FlashcardStatus.ACTIVE
            )
        )
        val learnedId = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What is normal GFR in a healthy adult?",
                backText = "Approximately 125 mL/min.",
                subjectId = subjectId,
                status = FlashcardStatus.LEARNED
            )
        )
        val otherSubjectCardId = dao.insertCard(
            Flashcard(
                type = FlashcardTypes.EXAM,
                word = "What is Frank-Starling law?",
                backText = "Stroke volume increases with end-diastolic volume.",
                subjectId = otherSubjectId,
                status = FlashcardStatus.ACTIVE
            )
        )

        // 1. Edit the flawed active question (preserves stageIndex = 2)
        val editSummary = QuestionBankRefinementParser.applyOperationToDatabase(
            subjectId = subjectId,
            operation = RefinementOperation.Edit(
                targetId = flawedActiveId,
                question = "How does the glomerular filtration barrier selectively restrict plasma proteins?",
                answer = "Fenestrated endothelium, negatively charged glomerular basement membrane, and podocyte slit diaphragms block proteins by size and negative charge."
            ),
            flashcardDao = dao
        )
        assertEquals(1, editSummary.editedCount)
        val editedCard = dao.getCardById(flawedActiveId)
        assertNotNull(editedCard)
        assertEquals(2, editedCard?.stageIndex)
        assertTrue(editedCard?.word?.contains("glomerular filtration barrier") == true)

        // 2. Combine frag1Id + frag2Id -> updates frag1Id (taking earliest stageIndex = 1) and deletes frag2Id
        val combineSummary = QuestionBankRefinementParser.applyOperationToDatabase(
            subjectId = subjectId,
            operation = RefinementOperation.Combine(
                sourceIds = listOf(frag1Id, frag2Id),
                question = "How does aldosterone simultaneously regulate Na+ and K+ transport in principal cells?",
                answer = "Aldosterone upregulates ENaC and basolateral Na+/K+-ATPase to increase Na+ reabsorption while driving lumen-negative K+ secretion."
            ),
            flashcardDao = dao
        )
        assertEquals(1, combineSummary.combinedCount)
        assertEquals(setOf(frag2Id), combineSummary.mergedAwayCardIds)
        assertNull(dao.getCardById(frag2Id))
        val mergedPrimary = dao.getCardById(frag1Id)
        assertNotNull(mergedPrimary)
        assertEquals(1, mergedPrimary?.stageIndex)
        assertEquals(30_000L, mergedPrimary?.nextReviewTimeMillis)
        assertTrue(mergedPrimary?.word?.contains("principal cells") == true)

        // 3. Attempted edit on LEARNED card or another subject's card must be rejected safely
        val rejectedLearnedEdit = QuestionBankRefinementParser.applyOperationToDatabase(
            subjectId = subjectId,
            operation = RefinementOperation.Edit(
                targetId = learnedId,
                question = "Mutated learned question",
                answer = "Mutated"
            ),
            flashcardDao = dao
        )
        assertFalse(rejectedLearnedEdit.hasAnyChange)
        assertEquals("What is normal GFR in a healthy adult?", dao.getCardById(learnedId)?.word)

        val rejectedCrossSubjectEdit = QuestionBankRefinementParser.applyOperationToDatabase(
            subjectId = subjectId,
            operation = RefinementOperation.Edit(
                targetId = otherSubjectCardId,
                question = "Mutated cardiology question",
                answer = "Mutated"
            ),
            flashcardDao = dao
        )
        assertFalse(rejectedCrossSubjectEdit.hasAnyChange)
        assertEquals("What is Frank-Starling law?", dao.getCardById(otherSubjectCardId)?.word)

        // 4. Add a new complementary exam-detail question
        val addSummary = QuestionBankRefinementParser.applyOperationToDatabase(
            subjectId = subjectId,
            operation = RefinementOperation.Add(
                question = "What percentage of filtered sodium is reabsorbed in the proximal convoluted tubule?",
                answer = "Approximately 65–70% of filtered Na+ (along with water, glucose, and amino acids)."
            ),
            flashcardDao = dao,
            createdAtMillis = 9_000L,
            nowMillis = 10_000L
        )
        assertEquals(1, addSummary.addedCount)

        val finalSubjectCards = dao.getCardsBySubjectSnapshot(subjectId)
        // Expected in Renal Physiology: flawedActive (edited), frag1 (combined), learned, + 1 newly added = 4
        assertEquals(4, finalSubjectCards.size)
    }

    @Test
    fun buildSubjectQuestionRefinementPrompt_includesEditableActiveIdsReadOnlyStatusesAndExamDetailGuidance() {
        val cards = listOf(
            Flashcard(
                id = 77L,
                type = FlashcardTypes.EXAM,
                word = "Active question text",
                backText = "Active answer text",
                subjectId = 5L,
                status = FlashcardStatus.ACTIVE
            ),
            Flashcard(
                id = 78L,
                type = FlashcardTypes.EXAM,
                word = "Mastered question text",
                backText = "Mastered answer",
                subjectId = 5L,
                status = FlashcardStatus.LEARNED
            ),
            Flashcard(
                id = 79L,
                type = FlashcardTypes.EXAM,
                word = "Rejected question text",
                backText = "Rejected answer",
                subjectId = 5L,
                status = FlashcardStatus.DUMB
            )
        )

        val prompt = GeminiRepository.buildSubjectQuestionRefinementPrompt(
            subjectName = "Computer Networks",
            textbook = "TCP uses a 3-way handshake (SYN, SYN-ACK, ACK) and port numbers are 16-bit unsigned integers.",
            extraKnowledge = "RFC 793 background history.",
            currentCards = cards
        )

        assertTrue(prompt.contains("[ID: 77]"))
        assertTrue(prompt.contains("Active question text"))
        assertTrue(prompt.contains("[L1] Mastered question text"))
        assertTrue(prompt.contains("[D1] Rejected question text"))
        assertTrue(prompt.contains("DIFFERENT WAYS OF THINKING"))
        assertTrue(prompt.contains("DO NOT OVER-MERGE"))
        assertTrue(prompt.contains("[action] EDIT [/action]"))
        assertTrue(prompt.contains("[action] COMBINE [/action]"))
        assertTrue(prompt.contains("[action] ADD [/action]"))
    }
}
