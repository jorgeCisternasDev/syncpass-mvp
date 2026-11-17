package cl.syncpass.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("startAt")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val name: String,
    val description: String = "",
    val venue: String,
    val address: String = "",
    val startAt: Long,
    val endAt: Long,
    /** 0 significa sin límite de aforo. */
    val capacity: Int = 0,
    val allowWalkIn: Boolean = false,
    val surveyEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val surveyReleased: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("eventId"),
        Index(value = ["eventId", "code"], unique = true),
        Index(value = ["eventId", "documentNormalized"], unique = true)
    ]
)
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val code: String,
    val name: String,
    val document: String,
    val documentNormalized: String,
    val email: String? = null,
    val phone: String? = null,
    val isWalkIn: Boolean = false,
    val registeredAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["participantId"], unique = true), Index("eventId")]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val participantId: Long,
    val attendedAt: Long = System.currentTimeMillis(),
    val method: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(defaultValue = "0")
    val sincronizado: Boolean = false
)

@Entity(
    tableName = "surveys",
    foreignKeys = [
        ForeignKey(
            entity = AttendanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["attendanceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["attendanceId"], unique = true),
        Index("eventId"),
        Index("participantId")
    ]
)
data class SurveyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attendanceId: Long,
    val eventId: Long,
    val participantId: Long,
    /** 1 a 5. */
    val satisfaction: Int,
    /** 1 a 5. */
    val usefulness: Int,
    val comment: String = "",
    val submittedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val sincronizado: Boolean = false
)

object AttendanceMethod {
    const val MANUAL = "MANUAL"
    const val QR = "QR"
    const val WALK_IN = "WALK_IN"
}
