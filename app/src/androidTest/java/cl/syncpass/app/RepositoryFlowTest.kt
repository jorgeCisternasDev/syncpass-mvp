package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.repository.AccreditResult
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.AddParticipantResult
import cl.syncpass.app.data.repository.EventInput
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.SaveEventResult
import cl.syncpass.app.data.repository.SubmitSurveyResult
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var eventRepository: EventRepository
    private lateinit var participantRepository: ParticipantRepository
    private lateinit var accreditationRepository: AccreditationRepository
    private lateinit var surveyRepository: SurveyRepository
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val projectRepository = ProjectRepository(db.projectDao())
        eventRepository = EventRepository(db.eventDao(), db.projectDao())
        participantRepository = ParticipantRepository(
            db.eventDao(),
            db.participantDao(),
            db.attendanceDao()
        )
        accreditationRepository = AccreditationRepository(
            eventDao = db.eventDao(),
            participantDao = db.participantDao(),
            attendanceDao = db.attendanceDao(),
            participantRepository = participantRepository,
            nowProvider = { now }
        )
        surveyRepository = SurveyRepository(
            attendanceDao = db.attendanceDao(),
            surveyDao = db.surveyDao(),
            nowProvider = { now }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullFlowFromRegistrationToIndicators() = runBlocking {
        val saved = eventRepository.save(
            eventId = null,
            input = EventInput(
                name = "Taller de prueba",
                description = "",
                venue = "Sede central",
                address = "",
                startAt = now - 60 * 60 * 1000L,
                endAt = now + 2 * 60 * 60 * 1000L,
                capacity = 10,
                allowWalkIn = true,
                surveyEnabled = true,
                projectId = null
            )
        )
        assertTrue(saved is SaveEventResult.Success)
        val eventId = (saved as SaveEventResult.Success).eventId
        assertTrue(eventRepository.setSurveyReleased(eventId, true))

        val added = participantRepository.add(
            eventId = eventId,
            name = "Ana Pérez",
            document = "12.345.678-9",
            email = "ana@example.cl"
        )
        assertTrue(added is AddParticipantResult.Success)
        val participant = (added as AddParticipantResult.Success).participant

        val firstAccreditation = accreditationRepository.accredit(
            eventId = eventId,
            rawInput = "12.345.678-9",
            method = AttendanceMethod.MANUAL,
            latitude = -33.4489,
            longitude = -70.6693
        )
        assertTrue(firstAccreditation is AccreditResult.Success)
        val attendance = (firstAccreditation as AccreditResult.Success).attendance
        assertEquals(participant.id, attendance.participantId)
        assertNotNull(attendance.latitude)

        val duplicate = accreditationRepository.accredit(
            eventId = eventId,
            rawInput = participant.code,
            method = AttendanceMethod.QR
        )
        assertTrue(duplicate is AccreditResult.Duplicate)

        val pending = surveyRepository.observePending().first()
        assertEquals(1, pending.size)
        assertEquals("Ana Pérez", pending.first().participantName)

        val submitted = surveyRepository.submit(
            attendanceId = attendance.id,
            satisfaction = 5,
            usefulness = 4,
            comment = "Muy buen taller"
        )
        assertTrue(submitted is SubmitSurveyResult.Success)
        assertTrue((submitted as SubmitSurveyResult.Success).created)

        val updated = surveyRepository.submit(
            attendanceId = attendance.id,
            satisfaction = 4,
            usefulness = 4,
            comment = "Comentario actualizado"
        )
        assertTrue(updated is SubmitSurveyResult.Success)
        assertEquals(false, (updated as SubmitSurveyResult.Success).created)

        val stats = surveyRepository.observeStats(eventId).first()
        assertEquals(1, stats.total)
        assertEquals(4.0, stats.avgSatisfaction ?: 0.0, 0.001)
        val distribution = surveyRepository.observeDistribution(eventId).first()
        assertEquals(1, distribution.firstOrNull { it.rating == 4 }?.count)

        val pendingAfter = surveyRepository.observePending().first()
        assertTrue(pendingAfter.isEmpty())

        val walkIn = accreditationRepository.accredit(
            eventId = eventId,
            rawInput = "99888777",
            method = AttendanceMethod.MANUAL
        )
        assertTrue(walkIn is AccreditResult.NeedsRegistration)

        val walkInDone = accreditationRepository.registerWalkInAndAccredit(
            eventId = eventId,
            name = "Pedro Soto",
            document = "99888777",
            email = ""
        )
        assertTrue(walkInDone is AccreditResult.Success)

        val summary = eventRepository.observeEvent(eventId).first()
        assertEquals(2, summary?.registeredCount)
        assertEquals(2, summary?.attendedCount)
        assertEquals(1, summary?.surveyCount)

        eventRepository.delete(eventId)
        assertEquals(0, db.eventDao().observeAllWithStats().first().size)
        assertEquals(0, db.participantDao().observeByEvent(eventId).first().size)
        assertEquals(0, db.attendanceDao().countByEvent(eventId))
        assertEquals(0, db.surveyDao().countByEvent(eventId))
    }

    @Test
    fun automaticReleaseAtEventEndAndManualButtonDoesNotDuplicate() = runBlocking {
        val clock = 1_000_000L
        val repository = EventRepository(
            eventDao = db.eventDao(),
            projectDao = db.projectDao(),
            nowProvider = { clock }
        )
        val saved = repository.save(
            eventId = null,
            input = EventInput(
                name = "Taller finalizado",
                description = "",
                venue = "Sede",
                address = "",
                startAt = clock - 2 * 60 * 60 * 1000L,
                endAt = clock - 1,
                capacity = 20,
                allowWalkIn = false,
                surveyEnabled = true,
                projectId = null
            )
        )
        assertTrue(saved is SaveEventResult.Success)
        val eventId = (saved as SaveEventResult.Success).eventId

        val released = repository.observeEvents().first().first { it.event.id == eventId }
        assertTrue(released.event.surveyReleased)
        val afterAuto = db.eventDao().getById(eventId)!!
        assertTrue(afterAuto.surveyReleased)
        val automaticUpdatedAt = afterAuto.updatedAt

        assertTrue(repository.releaseSurvey(eventId))
        val afterManual = db.eventDao().getById(eventId)!!
        assertTrue(afterManual.surveyReleased)
        assertEquals(automaticUpdatedAt, afterManual.updatedAt)
    }
}
