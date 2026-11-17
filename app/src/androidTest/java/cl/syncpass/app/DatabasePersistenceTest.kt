package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.local.SurveyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabasePersistenceTest {

    private val dbName = "syncpass-persistence-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val now = System.currentTimeMillis()
        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

        val projectId = db.projectDao().insert(ProjectEntity(name = "Proyecto de prueba"))
        val eventId = db.eventDao().insert(
            EventEntity(
                projectId = projectId,
                name = "Evento persistente",
                venue = "Sede",
                startAt = now - 60 * 60 * 1000L,
                endAt = now + 2 * 60 * 60 * 1000L,
                capacity = 20
            )
        )
        val participantId = db.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPTEST01",
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
                method = AttendanceMethod.MANUAL,
                latitude = -33.4489,
                longitude = -70.6693
            )
        )
        db.surveyDao().insert(
            SurveyEntity(
                attendanceId = attendanceId,
                eventId = eventId,
                participantId = participantId,
                satisfaction = 5,
                usefulness = 4,
                comment = "Muy buen evento"
            )
        )
        db.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val event = db.eventDao().getById(eventId)
        assertEquals("Evento persistente", event?.name)
        assertEquals(projectId, event?.projectId)

        val participant = db.participantDao().findByCode(eventId, "SPTEST01")
        assertEquals("Ana Pérez", participant?.name)

        val attendance = db.attendanceDao().getByParticipant(participantId)
        assertEquals(-33.4489, attendance?.latitude ?: 0.0, 0.0001)

        val survey = db.surveyDao().getByAttendance(attendanceId)
        assertEquals(5, survey?.satisfaction)
        assertEquals(4, survey?.usefulness)

        val stats = db.surveyDao().observeEventStats(eventId).first()
        assertEquals(1, stats.total)
        assertEquals(5.0, stats.avgSatisfaction ?: 0.0, 0.001)
        assertEquals(4.0, stats.avgUsefulness ?: 0.0, 0.001)

        val pending = db.attendanceDao().observePendingSurveys().first()
        assertTrue(pending.isEmpty())
        db.close()
    }
}
