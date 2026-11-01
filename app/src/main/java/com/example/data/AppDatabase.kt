package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Flashcard::class, Subject::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the back face used by TEXT cards without dropping the user's deck.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN backText TEXT")
            }
        }

        /**
         * Adds the subject centre: a subjects table plus the owning subject on each question card.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN subjectId INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subjects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds Textbook (answer reference) and Extra Knowledge (backing/quotable context) to subjects.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN textbook TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subjects ADD COLUMN extraKnowledge TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Adds lifecycle status (`ACTIVE`, `DUMB`, `LEARNED`) to flashcards so Dumb and Learned
         * items exit active review while remaining recorded against future duplicate generation.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }

        /**
         * Adds image attachment + OCR JSON lists for Textbook and Extra Knowledge on subjects.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN textbookImagesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE subjects ADD COLUMN extraKnowledgeImagesJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashrecall_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
