package cl.syncpass.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.local.SurveyEntity
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ExportResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExportRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        file = File(context.cacheDir, "syncpass-export-test.csv")
        file.delete()
    }

    @After
    fun tearDown() {
        file.delete()
        db.close()
    }

    @Test
    fun writesCsvWithBomAndEscapedFields() = runBlocking {
        val now = System.currentTimeMillis()
        val eventId = db.eventDao().insert(
            EventEntity(
                name = "Taller, \"avanzado\"",
                venue = "Sede",
                startAt = now - 3_600_000,
                endAt = now + 3_600_000
            )
        )
        val participantId = db.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPEXPORT",
                name = "Ana Pérez",
                document = "12.345.678-9",
                documentNormalized = "123456789"
            )
        )
        val attendanceId = db.attendanceDao().insert(
            AttendanceEntity(
                eventId = eventId,
                participantId = participantId,
                attendedAt = now,
                method = AttendanceMethod.MANUAL
            )
        )
        db.surveyDao().insert(
            SurveyEntity(
                attendanceId = attendanceId,
                eventId = eventId,
                participantId = participantId,
                satisfaction = 5,
                usefulness = 4,
                comment = "Muy bueno, \"excelente\"."
            )
        )

        val repository = ExportRepository(context, db.eventDao(), db.attendanceDao())
        val result = repository.writeCsv(Uri.fromFile(file), eventId)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).rows)
        val bytes = file.readBytes()
        assertTrue(bytes.size > 3)
        assertTrue(bytes[0] == 0xEF.toByte())
        assertTrue(bytes[1] == 0xBB.toByte())
        assertTrue(bytes[2] == 0xBF.toByte())
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("Ana Pérez"))
        assertTrue(text.contains("\"Taller, \"\"avanzado\"\"\""))
        assertTrue(text.contains("\"Muy bueno, \"\"excelente\"\".\""))
        assertTrue(text.contains("SPEXPORT"))
        assertTrue(text.contains("Manual"))
    }
}
