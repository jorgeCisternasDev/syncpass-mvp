package cl.syncpass.app.data.repository

import android.content.Context
import android.net.Uri
import cl.syncpass.app.data.local.AttendanceDao
import cl.syncpass.app.data.local.AttendanceExportRow
import cl.syncpass.app.data.local.EventDao
import cl.syncpass.app.util.CsvBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ExportResult {
    data class Success(val rows: Int) : ExportResult
    data class Failure(val message: String) : ExportResult
}

class ExportRepository(
    private val context: Context,
    private val eventDao: EventDao,
    private val attendanceDao: AttendanceDao
) {
    private suspend fun loadRows(eventId: Long?): List<AttendanceExportRow> =
        attendanceDao.getExportRows(eventId)

    suspend fun buildCsv(eventId: Long?): String = CsvBuilder.build(loadRows(eventId))

    suspend fun suggestedFileName(eventId: Long?): String {
        val eventName = eventId?.let { eventDao.getById(it)?.name }
        return CsvBuilder.fileName(eventName)
    }

    suspend fun writeCsv(uri: Uri, eventId: Long?): ExportResult = withContext(Dispatchers.IO) {
        try {
            val rows = loadRows(eventId)
            val csv = CsvBuilder.build(rows)
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(csv)
                }
            } ?: return@withContext ExportResult.Failure(
                "No se pudo abrir el archivo de destino."
            )
            ExportResult.Success(rows.size)
        } catch (error: Exception) {
            ExportResult.Failure(
                "No se pudo exportar el archivo: ${error.message ?: "error desconocido"}"
            )
        }
    }
}
