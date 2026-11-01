package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY nextReviewTimeMillis ASC")
    fun getAllCards(): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards ORDER BY id ASC")
    suspend fun getAllCardsSnapshot(): List<Flashcard>

    @Query("SELECT * FROM flashcards WHERE stageIndex = :stageIndex")
    fun getCardsByStage(stageIndex: Int): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getCardById(id: Long): Flashcard?

    @Query("SELECT COUNT(*) FROM flashcards")
    suspend fun getCardCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Flashcard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<Flashcard>)

    @Update
    suspend fun updateCard(card: Flashcard)

    @Query("UPDATE flashcards SET status = :status WHERE id = :cardId")
    suspend fun updateCardStatus(cardId: Long, status: String)

    @Delete
    suspend fun deleteCard(card: Flashcard)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteCardById(id: Long)

    @Query("DELETE FROM flashcards")
    suspend fun deleteAllCards()

    // --- Subject centre ---

    @Query("SELECT * FROM subjects ORDER BY createdAtMillis DESC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): Subject?

    @Query("SELECT * FROM subjects ORDER BY id ASC")
    suspend fun getAllSubjectsSnapshot(): List<Subject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSubjects(subjects: List<Subject>)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Query("UPDATE subjects SET textbook = :textbook WHERE id = :subjectId")
    suspend fun updateSubjectTextbook(subjectId: Long, textbook: String)

    @Query("UPDATE subjects SET extraKnowledge = :extraKnowledge WHERE id = :subjectId")
    suspend fun updateSubjectExtraKnowledge(subjectId: Long, extraKnowledge: String)

    @Query("UPDATE subjects SET textbookImagesJson = :imagesJson WHERE id = :subjectId")
    suspend fun updateSubjectTextbookImages(subjectId: Long, imagesJson: String)

    @Query("UPDATE subjects SET extraKnowledgeImagesJson = :imagesJson WHERE id = :subjectId")
    suspend fun updateSubjectExtraKnowledgeImages(subjectId: Long, imagesJson: String)

    @Query("DELETE FROM subjects WHERE id = :subjectId")
    suspend fun deleteSubjectById(subjectId: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query("SELECT * FROM flashcards WHERE subjectId = :subjectId ORDER BY id ASC")
    suspend fun getCardsBySubjectSnapshot(subjectId: Long): List<Flashcard>

    @Query("DELETE FROM flashcards WHERE subjectId = :subjectId")
    suspend fun deleteCardsBySubject(subjectId: Long)

    @Transaction
    suspend fun replaceAllMemory(subjects: List<Subject>, cards: List<Flashcard>) {
        deleteAllCards()
        deleteAllSubjects()
        if (subjects.isNotEmpty()) {
            insertAllSubjects(subjects)
        }
        if (cards.isNotEmpty()) {
            insertAll(cards)
        }
    }
}
