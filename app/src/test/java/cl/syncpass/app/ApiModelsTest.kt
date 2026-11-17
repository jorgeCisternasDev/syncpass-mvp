package cl.syncpass.app

import cl.syncpass.app.data.remote.AsistenciaRemota
import cl.syncpass.app.data.remote.EncuestaRemota
import cl.syncpass.app.data.remote.LoginRequest
import cl.syncpass.app.data.remote.LoginResponse
import cl.syncpass.app.data.remote.SincronizacionRequest
import cl.syncpass.app.data.remote.SincronizacionResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsTest {

    private val gson = Gson()

    @Test
    fun loginRequestSerializesExactlyEmailAndPassword() {
        val json = gson.toJson(LoginRequest("monitor@syncpass.cl", "Monitor.2026"))
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals(setOf("email", "password"), obj.keySet())
        assertEquals("monitor@syncpass.cl", obj.get("email").asString)
        assertEquals("Monitor.2026", obj.get("password").asString)
    }

    @Test
    fun loginResponseReadsFlatContractWithoutWrapper() {
        val body = """
            {"accessToken":"a.b.c","refreshToken":"r.e.f","idToken":"i.d.t"}
        """.trimIndent()

        val response = gson.fromJson(body, LoginResponse::class.java)

        assertEquals("a.b.c", response.accessToken)
        assertEquals("r.e.f", response.refreshToken)
        assertEquals("i.d.t", response.idToken)
    }

    @Test
    fun syncRequestSerializesAsistenciasAndEncuestasAtRoot() {
        val request = SincronizacionRequest(
            asistencias = listOf(
                AsistenciaRemota(
                    eventoId = 2,
                    usuarioId = 4,
                    timestamp = 1_700_000_000_000,
                    validado = true,
                    latitud = -33.51,
                    longitud = -70.60
                )
            ),
            encuestas = listOf(
                EncuestaRemota(
                    eventoId = 2,
                    usuarioId = 4,
                    satisfaccion = 5,
                    utilidad = 4,
                    comentario = "Buen taller",
                    fecha = 1_700_000_100_000
                )
            )
        )

        val obj = JsonParser.parseString(gson.toJson(request)).asJsonObject

        assertEquals(setOf("asistencias", "encuestas"), obj.keySet())
        val asistencia = obj.getAsJsonArray("asistencias")[0].asJsonObject
        assertEquals(
            setOf("eventoId", "usuarioId", "timestamp", "validado", "latitud", "longitud"),
            asistencia.keySet()
        )
        assertEquals(2L, asistencia.get("eventoId").asLong)
        assertEquals(4L, asistencia.get("usuarioId").asLong)
        assertTrue(asistencia.get("validado").asBoolean)

        val encuesta = obj.getAsJsonArray("encuestas")[0].asJsonObject
        assertEquals(
            setOf("eventoId", "usuarioId", "satisfaccion", "utilidad", "comentario", "fecha"),
            encuesta.keySet()
        )
        assertEquals(5, encuesta.get("satisfaccion").asInt)
        assertEquals(4, encuesta.get("utilidad").asInt)
    }

    @Test
    fun syncResponseReadsFlatAceptados() {
        val response = gson.fromJson("""{"aceptados":3}""", SincronizacionResponse::class.java)

        assertEquals(3, response.aceptados)
        assertEquals(0, gson.fromJson("{}", SincronizacionResponse::class.java).aceptados)
    }

    @Test
    fun loginResponseDoesNotRequireWrapperFields() {
        val response = gson.fromJson("""{"accessToken":"solo"}""", LoginResponse::class.java)

        assertEquals("solo", response.accessToken)
        assertFalse(response.accessToken.isNullOrBlank())
    }
}
