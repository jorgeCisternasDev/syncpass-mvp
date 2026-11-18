package cl.syncpass.app

import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.SurveyEntity
import cl.syncpass.app.data.repository.toRemote
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMappingTest {

    private val gson = Gson()

    @Test
    fun attendanceEntityMapsToRemoteContract() {
        val entity = AttendanceEntity(
            id = 7,
            eventId = 2,
            participantId = 9,
            attendedAt = 1_700_000_000_000,
            method = "QR",
            latitude = -33.51,
            longitude = -70.60
        )

        val remote = entity.toRemote()
        val json = JsonParser.parseString(gson.toJson(remote)).asJsonObject

        assertEquals(2L, remote.eventoId)
        assertEquals(9L, remote.usuarioId)
        assertEquals(1_700_000_000_000, remote.timestamp)
        assertTrue(remote.validado)
        assertEquals(-33.51, remote.latitud!!, 0.0001)
        assertEquals(-70.60, remote.longitud!!, 0.0001)
        assertEquals(
            setOf("eventoId", "usuarioId", "timestamp", "validado", "latitud", "longitud"),
            json.keySet()
        )
        assertEquals(9L, json.get("usuarioId").asLong)
    }

    @Test
    fun surveyEntityMapsToRemoteContractAndDropsBlankComment() {
        val entity = SurveyEntity(
            id = 3,
            attendanceId = 7,
            eventId = 2,
            participantId = 9,
            satisfaction = 5,
            usefulness = 4,
            comment = "   ",
            submittedAt = 1_700_000_100_000
        )

        val remote = entity.toRemote()
        val json = JsonParser.parseString(gson.toJson(remote)).asJsonObject

        assertEquals(2L, remote.eventoId)
        assertEquals(9L, remote.usuarioId)
        assertEquals(5, remote.satisfaccion)
        assertEquals(4, remote.utilidad)
        assertNull(remote.comentario)
        assertEquals(1_700_000_100_000, remote.fecha)
        assertEquals(
            setOf("eventoId", "usuarioId", "satisfaccion", "utilidad", "fecha"),
            json.keySet()
        )
    }

    @Test
    fun provisionedUuidTravelsInAttendanceAndSurveyJson() {
        val attendance = AttendanceEntity(
            id = 7,
            eventId = 2,
            participantId = 9,
            attendedAt = 1_700_000_000_000,
            method = "QR"
        ).toRemote(usuarioUuid = "11111111-1111-4111-8111-111111111111")

        val survey = SurveyEntity(
            id = 3,
            attendanceId = 7,
            eventId = 2,
            participantId = 9,
            satisfaction = 5,
            usefulness = 4,
            submittedAt = 1_700_000_100_000
        ).toRemote(usuarioUuid = "11111111-1111-4111-8111-111111111111")

        val attendanceJson = JsonParser.parseString(gson.toJson(attendance)).asJsonObject
        val surveyJson = JsonParser.parseString(gson.toJson(survey)).asJsonObject

        assertEquals(
            setOf("eventoId", "usuarioId", "usuarioUuid", "timestamp", "validado"),
            attendanceJson.keySet()
        )
        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            attendanceJson.get("usuarioUuid").asString
        )
        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            surveyJson.get("usuarioUuid").asString
        )
    }
}
