package cl.syncpass.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        EventEntity::class,
        ParticipantEntity::class,
        AttendanceEntity::class,
        SurveyEntity::class,
        SurveyDefinitionEntity::class,
        SurveyQuestionEntity::class,
        SurveyResponseEntity::class,
        SurveyAnswerEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun projectDao(): ProjectDao
    abstract fun participantDao(): ParticipantDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun surveyDao(): SurveyDao
    abstract fun statsDao(): StatsDao
    abstract fun customSurveyDao(): CustomSurveyDao

    companion object {
        const val NAME = "syncpass.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `survey_definitions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` INTEGER,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`eventId`) REFERENCES `events`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_definitions_eventId` " +
                        "ON `survey_definitions` (`eventId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_definitions_createdAt` " +
                        "ON `survey_definitions` (`createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_definitions_isActive` " +
                        "ON `survey_definitions` (`isActive`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `survey_questions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `surveyId` INTEGER NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `required` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`surveyId`) REFERENCES `survey_definitions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_questions_surveyId` " +
                        "ON `survey_questions` (`surveyId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_survey_questions_surveyId_position` " +
                        "ON `survey_questions` (`surveyId`, `position`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `survey_responses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `surveyId` INTEGER NOT NULL,
                        `respondentName` TEXT NOT NULL,
                        `submittedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`surveyId`) REFERENCES `survey_definitions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_responses_surveyId` " +
                        "ON `survey_responses` (`surveyId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_responses_submittedAt` " +
                        "ON `survey_responses` (`submittedAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `survey_answers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `responseId` INTEGER NOT NULL,
                        `questionId` INTEGER NOT NULL,
                        `textValue` TEXT,
                        `ratingValue` INTEGER,
                        FOREIGN KEY(`responseId`) REFERENCES `survey_responses`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`questionId`) REFERENCES `survey_questions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_answers_responseId` " +
                        "ON `survey_answers` (`responseId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_survey_answers_questionId` " +
                        "ON `survey_answers` (`questionId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_survey_answers_responseId_questionId` " +
                        "ON `survey_answers` (`responseId`, `questionId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `events` ADD COLUMN `surveyReleased` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `attendance` ADD COLUMN `sincronizado` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `surveys` ADD COLUMN `sincronizado` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN remoteId INTEGER")
                db.execSQL("ALTER TABLE projects ADD COLUMN areaTematica TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN unidadVecinal TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN fechaInicio INTEGER")
                db.execSQL("ALTER TABLE projects ADD COLUMN fechaTermino INTEGER")
                db.execSQL("ALTER TABLE projects ADD COLUMN estado TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_projects_remoteId ON projects(remoteId)"
                )

                db.execSQL("ALTER TABLE events ADD COLUMN remoteId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN remoteProjectId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN latitud REAL")
                db.execSQL("ALTER TABLE events ADD COLUMN longitud REAL")
                db.execSQL("ALTER TABLE events ADD COLUMN radioMetros INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_events_remoteId ON events(remoteId)"
                )

                db.execSQL("ALTER TABLE participants ADD COLUMN usuarioUuid TEXT")
                db.execSQL("ALTER TABLE participants ADD COLUMN provisioned INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_participants_usuarioUuid " +
                        "ON participants(usuarioUuid)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
