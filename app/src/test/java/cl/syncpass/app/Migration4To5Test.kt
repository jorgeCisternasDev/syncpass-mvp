package cl.syncpass.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import cl.syncpass.app.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Crea una base v4 real (esquema generado por Room en la version anterior),
 * la abre con la app v5 y comprueba que la migracion 4 -> 5 conserva filas y
 * agrega columnas con los valores por defecto del diseno H3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration4To5Test {

    @Test
    fun migracion4a5ConservaDatosYAgregaColumnasNuevas() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "migracion-h3.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()

        val v4 = SQLiteDatabase.openOrCreateDatabase(path, null)
        V4_SCHEMA.forEach { v4.execSQL(it) }
        v4.execSQL(
            "INSERT INTO projects (id, name, description, createdAt) " +
                "VALUES (1, 'Proyecto local', '', 100)"
        )
        v4.execSQL(
            "INSERT INTO events (id, projectId, name, description, venue, address, startAt, " +
                "endAt, capacity, allowWalkIn, surveyEnabled, surveyReleased, createdAt, " +
                "updatedAt) VALUES (1, 1, 'Evento local', '', 'Sede', '', 1000, 2000, 0, 0, 1, 0, 100, 100)"
        )
        v4.execSQL(
            "INSERT INTO participants (id, eventId, code, name, document, documentNormalized, " +
                "email, phone, isWalkIn, registeredAt) " +
                "VALUES (1, 1, 'SPABC123', 'Ficticio', '11111111-1', '111111111', NULL, NULL, 0, 100)"
        )
        v4.version = 4
        v4.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()
        try {
            val project = database.projectDao().getById(1)!!
            assertEquals("Proyecto local", project.name)
            assertNull(project.remoteId)
            assertEquals("", project.areaTematica)
            assertEquals("", project.unidadVecinal)
            assertNull(project.fechaInicio)
            assertNull(project.fechaTermino)
            assertEquals("", project.estado)

            val event = database.eventDao().getById(1)!!
            assertEquals("Evento local", event.name)
            assertNull(event.remoteId)
            assertNull(event.remoteProjectId)
            assertNull(event.latitud)
            assertNull(event.longitud)
            assertEquals(0, event.radioMetros)

            val participant = database.participantDao().getById(1)!!
            assertEquals("Ficticio", participant.name)
            assertNull(participant.usuarioUuid)
            assertFalse(participant.provisioned)

            assertEquals(1, database.projectDao().observeAll().first().size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private companion object {
        /** Esquema exacto que generaba Room 2.8.5 para la version 4. */
        val V4_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `venue` TEXT NOT NULL, `address` TEXT NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `allowWalkIn` INTEGER NOT NULL, `surveyEnabled` INTEGER NOT NULL, `surveyReleased` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_events_projectId` ON `events` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_events_startAt` ON `events` (`startAt`)",
            "CREATE TABLE IF NOT EXISTS `participants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `document` TEXT NOT NULL, `documentNormalized` TEXT NOT NULL, `email` TEXT, `phone` TEXT, `isWalkIn` INTEGER NOT NULL, `registeredAt` INTEGER NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_participants_eventId` ON `participants` (`eventId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_participants_eventId_code` ON `participants` (`eventId`, `code`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_participants_eventId_documentNormalized` ON `participants` (`eventId`, `documentNormalized`)",
            "CREATE TABLE IF NOT EXISTS `attendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `participantId` INTEGER NOT NULL, `attendedAt` INTEGER NOT NULL, `method` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `sincronizado` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`participantId`) REFERENCES `participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_participantId` ON `attendance` (`participantId`)",
            "CREATE INDEX IF NOT EXISTS `index_attendance_eventId` ON `attendance` (`eventId`)",
            "CREATE TABLE IF NOT EXISTS `surveys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `attendanceId` INTEGER NOT NULL, `eventId` INTEGER NOT NULL, `participantId` INTEGER NOT NULL, `satisfaction` INTEGER NOT NULL, `usefulness` INTEGER NOT NULL, `comment` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, `sincronizado` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`attendanceId`) REFERENCES `attendance`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`participantId`) REFERENCES `participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_surveys_attendanceId` ON `surveys` (`attendanceId`)",
            "CREATE INDEX IF NOT EXISTS `index_surveys_eventId` ON `surveys` (`eventId`)",
            "CREATE INDEX IF NOT EXISTS `index_surveys_participantId` ON `surveys` (`participantId`)",
            "CREATE TABLE IF NOT EXISTS `survey_definitions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_survey_definitions_eventId` ON `survey_definitions` (`eventId`)",
            "CREATE INDEX IF NOT EXISTS `index_survey_definitions_createdAt` ON `survey_definitions` (`createdAt`)",
            "CREATE INDEX IF NOT EXISTS `index_survey_definitions_isActive` ON `survey_definitions` (`isActive`)",
            "CREATE TABLE IF NOT EXISTS `survey_questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surveyId` INTEGER NOT NULL, `prompt` TEXT NOT NULL, `type` TEXT NOT NULL, `required` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`surveyId`) REFERENCES `survey_definitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_survey_questions_surveyId` ON `survey_questions` (`surveyId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_survey_questions_surveyId_position` ON `survey_questions` (`surveyId`, `position`)",
            "CREATE TABLE IF NOT EXISTS `survey_responses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surveyId` INTEGER NOT NULL, `respondentName` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, FOREIGN KEY(`surveyId`) REFERENCES `survey_definitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_survey_responses_surveyId` ON `survey_responses` (`surveyId`)",
            "CREATE INDEX IF NOT EXISTS `index_survey_responses_submittedAt` ON `survey_responses` (`submittedAt`)",
            "CREATE TABLE IF NOT EXISTS `survey_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `responseId` INTEGER NOT NULL, `questionId` INTEGER NOT NULL, `textValue` TEXT, `ratingValue` INTEGER, FOREIGN KEY(`responseId`) REFERENCES `survey_responses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`questionId`) REFERENCES `survey_questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_survey_answers_responseId` ON `survey_answers` (`responseId`)",
            "CREATE INDEX IF NOT EXISTS `index_survey_answers_questionId` ON `survey_answers` (`questionId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_survey_answers_responseId_questionId` ON `survey_answers` (`responseId`, `questionId`)"
        )
    }
}
