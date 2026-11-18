package cl.syncpass.app.data.repository

import androidx.room.withTransaction
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.remote.CatalogoApi
import cl.syncpass.app.data.remote.EventoRemoto
import cl.syncpass.app.data.remote.ProyectoRemoto
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface CatalogoResult {
    data class Success(val proyectos: Int, val eventos: Int) : CatalogoResult
    data class Failure(val message: String) : CatalogoResult
}

/**
 * Descarga el catalogo remoto y lo suma a Room.
 *
 * Reglas de H3:
 * - upsert por `remoteId`: repetir la descarga actualiza, no duplica;
 * - nunca borra proyectos ni eventos locales que no vengan del remoto;
 * - `endAt = fechaTermino ?: fechaHora + 2 h` (supuesto S-2 del diseno).
 */
class CatalogoRepository(
    private val database: AppDatabase,
    private val catalogoApi: CatalogoApi,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val projectDao = database.projectDao()
    private val eventDao = database.eventDao()

    suspend fun refrescar(): CatalogoResult {
        return try {
            val proyectos = catalogoApi.proyectos()
            val eventosPorProyecto = proyectos.associate { proyecto ->
                proyecto.proyectoId to catalogoApi.eventos(proyecto.proyectoId)
            }
            val timestamp = now()
            var eventos = 0
            database.withTransaction {
                proyectos.forEach { proyecto ->
                    val proyectoLocalId = upsertProyecto(proyecto, timestamp)
                    eventosPorProyecto[proyecto.proyectoId].orEmpty().forEach { evento ->
                        upsertEvento(evento, proyectoLocalId, timestamp)
                        eventos++
                    }
                }
            }
            CatalogoResult.Success(proyectos = proyectos.size, eventos = eventos)
        } catch (error: HttpException) {
            CatalogoResult.Failure("El servidor respondio ${error.code()} al descargar el catalogo.")
        } catch (error: IOException) {
            CatalogoResult.Failure("Sin conexion con el servidor al descargar el catalogo.")
        } catch (error: Exception) {
            CatalogoResult.Failure(
                "No se pudo descargar el catalogo: ${error.message ?: "error desconocido"}"
            )
        }
    }

    private suspend fun upsertProyecto(remoto: ProyectoRemoto, timestamp: Long): Long {
        val existente = projectDao.findByRemoteId(remoto.proyectoId)
        return if (existente == null) {
            projectDao.insert(
                ProjectEntity(
                    name = remoto.titulo,
                    description = remoto.descripcion,
                    createdAt = timestamp,
                    remoteId = remoto.proyectoId,
                    areaTematica = remoto.areaTematica,
                    unidadVecinal = remoto.unidadVecinal,
                    fechaInicio = remoto.fechaInicio,
                    fechaTermino = remoto.fechaTermino,
                    estado = remoto.estado
                )
            )
        } else {
            projectDao.update(
                existente.copy(
                    name = remoto.titulo,
                    description = remoto.descripcion,
                    areaTematica = remoto.areaTematica,
                    unidadVecinal = remoto.unidadVecinal,
                    fechaInicio = remoto.fechaInicio,
                    fechaTermino = remoto.fechaTermino,
                    estado = remoto.estado
                )
            )
            existente.id
        }
    }

    private suspend fun upsertEvento(
        remoto: EventoRemoto,
        proyectoLocalId: Long,
        timestamp: Long
    ) {
        val endAt = remoto.fechaTermino ?: (remoto.fechaHora + DURACION_POR_DEFECTO_MS)
        val existente = eventDao.findByRemoteId(remoto.eventoId)
        if (existente == null) {
            eventDao.insert(
                EventEntity(
                    projectId = proyectoLocalId,
                    name = remoto.nombre,
                    venue = remoto.recinto,
                    startAt = remoto.fechaHora,
                    endAt = endAt,
                    capacity = remoto.cuposMax,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    remoteId = remoto.eventoId,
                    remoteProjectId = remoto.proyectoId,
                    latitud = remoto.latitud,
                    longitud = remoto.longitud,
                    radioMetros = remoto.radioMetros
                )
            )
        } else {
            eventDao.update(
                existente.copy(
                    projectId = proyectoLocalId,
                    name = remoto.nombre,
                    venue = remoto.recinto,
                    startAt = remoto.fechaHora,
                    endAt = endAt,
                    capacity = remoto.cuposMax,
                    updatedAt = timestamp,
                    remoteProjectId = remoto.proyectoId,
                    latitud = remoto.latitud,
                    longitud = remoto.longitud,
                    radioMetros = remoto.radioMetros
                )
            )
        }
    }

    private companion object {
        /** Supuesto S-2: sin fechaTermino, la ventana dura dos horas. */
        val DURACION_POR_DEFECTO_MS: Long = TimeUnit.HOURS.toMillis(2)
    }
}
