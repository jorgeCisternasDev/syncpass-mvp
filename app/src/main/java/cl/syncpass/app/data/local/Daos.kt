package cl.syncpass.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query(
        """
        SELECT e.*,
            (SELECT COUNT(*) FROM participants p WHERE p.eventId = e.id) AS registeredCount,
            (SELECT COUNT(*) FROM attendance a WHERE a.eventId = e.id) AS attendedCount,
            (SELECT COUNT(*) FROM surveys s WHERE s.eventId = e.id) AS surveyCount
        FROM events e
        ORDER BY e.startAt ASC
        """
    )
    fun observeAllWithStats(): Flow<List<EventWithStats>>

    @Query(
        """
        SELECT e.*,
            (SELECT COUNT(*) FROM participants p WHERE p.eventId = e.id) AS registeredCount,
            (SELECT COUNT(*) FROM attendance a WHERE a.eventId = e.id) AS attendedCount,
            (SELECT COUNT(*) FROM surveys s WHERE s.eventId = e.id) AS surveyCount
        FROM events e
        WHERE e.id = :eventId
        """
    )
    fun observeByIdWithStats(eventId: Long): Flow<EventWithStats?>

    @Query("SELECT * FROM events WHERE id = :eventId")
    fun observeById(eventId: Long): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getById(eventId: Long): EventEntity?

    @Query("SELECT * FROM events WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Long): EventEntity?

    @Query(
        """
        UPDATE events
        SET surveyReleased = 1, updatedAt = :now
        WHERE surveyEnabled = 1 AND surveyReleased = 0 AND endAt <= :now
        """
    )
    suspend fun autoReleaseFinishedSurveys(now: Long): Int

    @Query("SELECT COUNT(*) FROM events")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)
}

@Dao
interface ProjectDao {
    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM events e WHERE e.projectId = p.id) AS eventCount
        FROM projects p
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeAllWithCount(): Flow<List<ProjectWithCount>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface ParticipantDao {
    @Query(
        """
        SELECT p.*,
            a.id AS attendanceId,
            a.attendedAt AS attendedAt,
            a.method AS method,
            a.latitude AS latitude,
            a.longitude AS longitude,
            s.id AS surveyId,
            s.satisfaction AS satisfaction,
            s.usefulness AS usefulness,
            s.submittedAt AS surveySubmittedAt
        FROM participants p
        LEFT JOIN attendance a ON a.participantId = p.id
        LEFT JOIN surveys s ON s.attendanceId = a.id
        WHERE p.eventId = :eventId
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeByEvent(eventId: Long): Flow<List<ParticipantStatusRow>>

    @Query("SELECT * FROM participants WHERE id = :participantId")
    suspend fun getById(participantId: Long): ParticipantEntity?

    @Query("SELECT * FROM participants WHERE eventId = :eventId AND code = :code LIMIT 1")
    suspend fun findByCode(eventId: Long, code: String): ParticipantEntity?

    @Query(
        "SELECT * FROM participants WHERE eventId = :eventId AND documentNormalized = :documentNormalized LIMIT 1"
    )
    suspend fun findByDocument(eventId: Long, documentNormalized: String): ParticipantEntity?

    @Query("SELECT COUNT(*) FROM participants WHERE eventId = :eventId")
    suspend fun countByEvent(eventId: Long): Int

    @Query("SELECT code FROM participants WHERE eventId = :eventId")
    suspend fun allCodes(eventId: Long): List<String>

    @Query("SELECT * FROM participants WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ParticipantEntity>

    @Query(
        """
        SELECT p.*, e.remoteId AS eventRemoteId
        FROM participants p
        INNER JOIN events e ON e.id = p.eventId
        WHERE p.provisioned = 0
        ORDER BY p.id ASC
        """
    )
    suspend fun pendingProvisioning(): List<ProvisioningParticipantRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(participant: ParticipantEntity): Long

    @Update
    suspend fun update(participant: ParticipantEntity)

    @Delete
    suspend fun delete(participant: ParticipantEntity)

    @Query("DELETE FROM participants WHERE id = :participantId")
    suspend fun deleteById(participantId: Long)
}

@Dao
interface AttendanceDao {
    @Query(
        """
        SELECT a.*, p.name AS participantName, p.document AS participantDocument, p.code AS participantCode
        FROM attendance a
        INNER JOIN participants p ON p.id = a.participantId
        WHERE a.eventId = :eventId
        ORDER BY a.attendedAt DESC
        """
    )
    fun observeByEvent(eventId: Long): Flow<List<AttendanceRow>>

    @Query("SELECT * FROM attendance WHERE id = :attendanceId")
    suspend fun getById(attendanceId: Long): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE participantId = :participantId LIMIT 1")
    suspend fun getByParticipant(participantId: Long): AttendanceEntity?

    @Query("SELECT COUNT(*) FROM attendance WHERE eventId = :eventId")
    suspend fun countByEvent(eventId: Long): Int

    @Query("SELECT * FROM attendance WHERE sincronizado = 0 ORDER BY attendedAt ASC")
    suspend fun pendingForSync(): List<AttendanceEntity>

    @Query("UPDATE attendance SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM attendance WHERE sincronizado = 0")
    fun observePendingSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attendance: AttendanceEntity): Long

    @Query("DELETE FROM attendance WHERE id = :attendanceId")
    suspend fun deleteById(attendanceId: Long): Int

    @Query(
        """
        SELECT a.id AS attendanceId, a.eventId AS eventId, e.name AS eventName,
            p.id AS participantId, p.name AS participantName, p.document AS participantDocument,
            a.attendedAt AS attendedAt
        FROM attendance a
        INNER JOIN events e ON e.id = a.eventId
        INNER JOIN participants p ON p.id = a.participantId
        LEFT JOIN surveys s ON s.attendanceId = a.id
        WHERE e.surveyEnabled = 1 AND e.surveyReleased = 1 AND s.id IS NULL
        ORDER BY a.attendedAt DESC
        """
    )
    fun observePendingSurveys(): Flow<List<PendingSurveyRow>>

    @Query(
        """
        SELECT a.id AS attendanceId, a.eventId AS eventId, e.name AS eventName,
            e.surveyEnabled AS eventSurveyEnabled,
            e.surveyReleased AS eventSurveyReleased,
            p.id AS participantId, p.name AS participantName, p.document AS participantDocument,
            a.attendedAt AS attendedAt,
            s.id AS surveyId, s.satisfaction AS satisfaction, s.usefulness AS usefulness,
            s.comment AS comment, s.submittedAt AS submittedAt
        FROM attendance a
        INNER JOIN events e ON e.id = a.eventId
        INNER JOIN participants p ON p.id = a.participantId
        LEFT JOIN surveys s ON s.attendanceId = a.id
        WHERE a.id = :attendanceId
        """
    )
    suspend fun getSurveyContext(attendanceId: Long): SurveyContextRow?

    @Query(
        """
        SELECT e.name AS eventName, e.startAt AS eventStartAt,
            p.name AS participantName, p.document AS participantDocument, p.code AS participantCode,
            p.isWalkIn AS isWalkIn, p.registeredAt AS registeredAt,
            a.attendedAt AS attendedAt, a.method AS method, a.latitude AS latitude, a.longitude AS longitude,
            s.satisfaction AS satisfaction, s.usefulness AS usefulness, s.comment AS comment
        FROM participants p
        INNER JOIN events e ON e.id = p.eventId
        LEFT JOIN attendance a ON a.participantId = p.id
        LEFT JOIN surveys s ON s.attendanceId = a.id
        WHERE (:eventId IS NULL OR e.id = :eventId)
        ORDER BY e.startAt ASC, p.name COLLATE NOCASE ASC
        """
    )
    suspend fun getExportRows(eventId: Long?): List<AttendanceExportRow>
}

@Dao
interface SurveyDao {
    @Query(
        """
        SELECT s.*, p.name AS participantName, p.document AS participantDocument, e.name AS eventName
        FROM surveys s
        INNER JOIN participants p ON p.id = s.participantId
        INNER JOIN events e ON e.id = s.eventId
        ORDER BY s.submittedAt DESC
        """
    )
    fun observeCompleted(): Flow<List<SurveyListRow>>

    @Query(
        """
        SELECT s.*, p.name AS participantName, p.document AS participantDocument, e.name AS eventName
        FROM surveys s
        INNER JOIN participants p ON p.id = s.participantId
        INNER JOIN events e ON e.id = s.eventId
        WHERE s.eventId = :eventId
        ORDER BY s.submittedAt DESC
        """
    )
    fun observeByEvent(eventId: Long): Flow<List<SurveyListRow>>

    @Query("SELECT * FROM surveys WHERE attendanceId = :attendanceId LIMIT 1")
    suspend fun getByAttendance(attendanceId: Long): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE id = :surveyId")
    suspend fun getById(surveyId: Long): SurveyEntity?

    @Query("SELECT COUNT(*) FROM surveys WHERE eventId = :eventId")
    suspend fun countByEvent(eventId: Long): Int

    @Query("SELECT COUNT(*) FROM surveys")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM surveys WHERE sincronizado = 0 ORDER BY submittedAt ASC")
    suspend fun pendingForSync(): List<SurveyEntity>

    @Query("UPDATE surveys SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM surveys WHERE sincronizado = 0")
    fun observePendingSyncCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS total,
            AVG(satisfaction) AS avgSatisfaction,
            AVG(usefulness) AS avgUsefulness
        FROM surveys WHERE eventId = :eventId
        """
    )
    fun observeEventStats(eventId: Long): Flow<SurveyStatsRow>

    @Query(
        """
        SELECT satisfaction AS rating, COUNT(*) AS count
        FROM surveys WHERE eventId = :eventId
        GROUP BY satisfaction
        ORDER BY satisfaction ASC
        """
    )
    fun observeDistribution(eventId: Long): Flow<List<RatingCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(survey: SurveyEntity): Long

    @Update
    suspend fun update(survey: SurveyEntity)
}

@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM events")
    fun observeEventCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM participants")
    fun observeParticipantCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance")
    fun observeAttendanceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM surveys")
    fun observeSurveyCount(): Flow<Int>
}
