package com.api.somoscardoso.messaging

import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

enum class NotificationStatus {
  PENDING,
  SENDING,
  SENT,
  CANCELLED,
}

enum class NotificationType {
  DRAW_RESULT,
  REGISTRATION,
}

@Entity
@Table(
    name = "whatsapp_notifications",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ss_player_id", "type"])],
)
class WhatsAppNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var ssPlayerId: Long = 0,
    @Column(length = 32) var phone: String = "",
    @Column(length = 120) var groupName: String = "",
    var recipientName: String = "",
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32, nullable = false)
    var type: NotificationType = NotificationType.DRAW_RESULT,
    var scheduledDrawAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    var status: NotificationStatus = NotificationStatus.PENDING,
    var attempts: Int = 0,
    var nextAttemptAt: Instant = Instant.EPOCH,
)

interface WhatsAppNotificationRepository : JpaRepository<WhatsAppNotification, Long> {
  fun findBySsPlayerIdAndType(ssPlayerId: Long, type: NotificationType): WhatsAppNotification?

  fun deleteAllBySsPlayerId(ssPlayerId: Long)

  @Query(
      "select n.id from WhatsAppNotification n where n.status not in (com.api.somoscardoso.messaging.NotificationStatus.SENT, com.api.somoscardoso.messaging.NotificationStatus.CANCELLED) and n.nextAttemptAt <= :now and n.type in :types and (n.type <> com.api.somoscardoso.messaging.NotificationType.REGISTRATION or n.scheduledDrawAt > :now) order by n.nextAttemptAt"
  )
  fun findDueIds(
      @Param("now") now: Instant,
      @Param("types") types: Collection<NotificationType>,
      pageable: org.springframework.data.domain.Pageable,
  ): List<Long>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from WhatsAppNotification n where n.id = :id")
  fun lockById(@Param("id") id: Long): WhatsAppNotification?
}
