package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.remote.CatalogoApi
import cl.syncpass.app.data.remote.EventoRemoto
import cl.syncpass.app.data.remote.ProyectoRemoto
import cl.syncpass.app.data.repository.CatalogoRepository
import cl.syncpass.app.data.repository.CatalogoResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogoRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var api: FakeCatalogoApi
    private lateinit var repository: CatalogoRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeCatalogoApi()
        repository = CatalogoRepository(database, api, now = { 1_700_000_000_000 })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun c01DescargaProyectosYLosInsertaConRemoteId() = runBlocking {
        api.proyectos = listOf(proyecto(10, "Taller de huertos"))

        val result = repository.refrescar()

        assertEquals(CatalogoResult.Success(proyectos = 1, eventos = 0), result)
        val guardado = database.projectDao().findByRemoteId(10)
        assertNotNull(guardado)
        assertEquals("Taller de huertos", guardado!!.name)
        assertEquals("Descripcion remota", guardado.description)
        assertEquals("Medio ambiente", guardado.areaTematica)
        assertEquals("Villa Norte", guardado.unidadVecinal)
        assertEquals(1_000L, guardado.fechaInicio)
        assertEquals(2_000L, guardado.fechaTermino)
        assertEquals("ACTIVO", guardado.estado)
    }

    @Test
    fun c02RepetirDescargaActualizaSinDuplicar() = runBlocking {
        api.proyectos = listOf(proyecto(10, "Taller de huertos"))
        repository.refrescar()
        val primera = database.projectDao().findByRemoteId(10)!!

        api.proyectos = listOf(proyecto(10, "Taller de huertos v2"))
        repository.refrescar()

        val proyectos = database.projectDao().observeAll().first()
        assertEquals(1, proyectos.size)
        assertEquals(primera.id, proyectos.single().id)
        assertEquals("Taller de huertos v2", proyectos.single().name)
    }

    @Test
    fun c03EventosSeAsocianAlProyectoLocalCorrecto() = runBlocking {
        api.proyectos = listOf(proyecto(10, "Uno"), proyecto(20, "Dos"))
        api.eventos = mapOf(
            20L to listOf(evento(eventoId = 200, proyectoId = 20, nombre = "Feria"))
        )

        repository.refrescar()

        val proyectoDos = database.projectDao().findByRemoteId(20)!!
        val eventoLocal = database.eventDao().findByRemoteId(200)
        assertNotNull(eventoLocal)
        assertEquals(proyectoDos.id, eventoLocal!!.projectId)
        assertEquals(20L, eventoLocal.remoteProjectId)
        assertEquals("Feria", eventoLocal.name)
        assertEquals("Recinto central", eventoLocal.venue)
        assertEquals(30, eventoLocal.capacity)
        assertEquals(-33.51, eventoLocal.latitud!!, 0.0001)
        assertEquals(-70.60, eventoLocal.longitud!!, 0.0001)
        assertEquals(120, eventoLocal.radioMetros)
    }

    @Test
    fun c04EventoSinFechaTerminoUsaDosHorasPorDefecto() = runBlocking {
        val inicio = 1_700_100_000_000
        api.proyectos = listOf(proyecto(10, "Uno"))
        api.eventos = mapOf(
            10L to listOf(
                evento(eventoId = 100, proyectoId = 10, fechaHora = inicio),
                evento(
                    eventoId = 101,
                    proyectoId = 10,
                    fechaHora = inicio,
                    fechaTermino = inicio + TimeUnit.MINUTES.toMillis(90)
                )
            )
        )

        repository.refrescar()

        val sinTermino = database.eventDao().findByRemoteId(100)!!
        val conTermino = database.eventDao().findByRemoteId(101)!!
        assertEquals(inicio + TimeUnit.HOURS.toMillis(2), sinTermino.endAt)
        assertEquals(inicio + TimeUnit.MINUTES.toMillis(90), conTermino.endAt)
    }

    @Test
    fun c05NoBorraProyectosNiEventosLocalesQueNoVienenDelRemoto() = runBlocking {
        val proyectoLocal = database.projectDao().insert(
            ProjectEntity(name = "Proyecto del telefono")
        )
        val eventoLocal = database.eventDao().insert(
            EventEntity(
                projectId = proyectoLocal,
                name = "Evento del telefono",
                venue = "Sede",
                startAt = 1_000,
                endAt = 2_000
            )
        )
        api.proyectos = listOf(proyecto(10, "Proyecto remoto"))

        repository.refrescar()

        assertNotNull(database.projectDao().getById(proyectoLocal))
        assertNotNull(database.eventDao().getById(eventoLocal))
        assertEquals(2, database.projectDao().observeAll().first().size)
        assertNull(database.eventDao().findByRemoteId(999))
    }

    @Test
    fun upsertPorRemoteIdEsIdempotenteEnTresDescargas() = runBlocking {
        api.proyectos = listOf(proyecto(10, "Uno"))
        api.eventos = mapOf(10L to listOf(evento(eventoId = 100, proyectoId = 10)))
        repository.refrescar()
        val primerProyecto = database.projectDao().findByRemoteId(10)!!
        val primerEvento = database.eventDao().findByRemoteId(100)!!

        repository.refrescar()
        repository.refrescar()

        val proyectos = database.projectDao().observeAll().first()
        val eventos = database.eventDao().observeAllWithStats().first()
        assertEquals(1, proyectos.size)
        assertEquals(1, eventos.size)
        assertEquals(primerProyecto.id, proyectos.single().id)
        assertEquals(primerEvento.id, eventos.single().event.id)
    }

    private fun proyecto(id: Long, titulo: String) = ProyectoRemoto(
        proyectoId = id,
        titulo = titulo,
        descripcion = "Descripcion remota",
        areaTematica = "Medio ambiente",
        unidadVecinal = "Villa Norte",
        fechaInicio = 1_000,
        fechaTermino = 2_000,
        estado = "ACTIVO"
    )

    private fun evento(
        eventoId: Long,
        proyectoId: Long,
        nombre: String = "Evento remoto",
        fechaHora: Long = 1_700_000_000_000,
        fechaTermino: Long? = null
    ) = EventoRemoto(
        eventoId = eventoId,
        proyectoId = proyectoId,
        nombre = nombre,
        fechaHora = fechaHora,
        fechaTermino = fechaTermino,
        cuposMax = 30,
        recinto = "Recinto central",
        latitud = -33.51,
        longitud = -70.60,
        radioMetros = 120
    )

    private class FakeCatalogoApi : CatalogoApi {
        var proyectos: List<ProyectoRemoto> = emptyList()
        var eventos: Map<Long, List<EventoRemoto>> = emptyMap()

        override suspend fun proyectos(): List<ProyectoRemoto> = proyectos

        override suspend fun eventos(proyectoId: Long): List<EventoRemoto> =
            eventos[proyectoId].orEmpty()
    }
}
