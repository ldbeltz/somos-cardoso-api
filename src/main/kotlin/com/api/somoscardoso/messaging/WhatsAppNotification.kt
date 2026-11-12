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
}

@Entity
@Table(
    name = "whatsapp_notifications",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ss_player_id"])],
)
class WhatsAppNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var ssPlayerId: Long = 0,
    @Column(length = 32) var phone: String = "",
    @Column(length = 120) var groupName: String = "",
    var recipientName: String = "",
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    var status: NotificationStatus = NotificationStatus.PENDING,
    var attempts: Int = 0,
    var nextAttemptAt: Instant = Instant.EPOCH,
)

interface WhatsAppNotificationRepository : JpaRepository<WhatsAppNotification, Long> {
  fun findBySsPlayerId(ssPlayerId: Long): WhatsAppNotification?

  @Query(
      "select n.id from WhatsAppNotification n where n.status <> com.api.somoscardoso.messaging.NotificationStatus.SENT and n.nextAttemptAt <= :now order by n.nextAttemptAt"
  )
  fun findDueIds(
      @Param("now") now: Instant,
      pageable: org.springframework.data.domain.Pageable,
  ): List<Long>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from WhatsAppNotification n where n.id = :id")
  fun lockById(@Param("id") id: Long): WhatsAppNotification?
}
