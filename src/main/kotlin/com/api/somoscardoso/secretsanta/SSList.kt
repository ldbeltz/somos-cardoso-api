package com.api.somoscardoso.secretsanta

import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

enum class DrawStatus {
  SCHEDULED,
  DRAWN,
}

@Entity
@Table(name = "ss_lists")
class SSList(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(length = 120) var name: String = "",
    var drawAt: Instant = Instant.EPOCH,
    var creatorId: Long = 0,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    var status: DrawStatus = DrawStatus.SCHEDULED,
    var drawnAt: Instant? = null,
)

@Entity
@Table(
    name = "ss_players",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ss_list_id", "player_id"])],
)
class SSPlayer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var ssListId: Long = 0,
    var playerId: Long = 0,
    var recipientId: Long? = null,
)

interface SSListRepository : JpaRepository<SSList, Long> {
  fun findAllByStatusAndDrawAtLessThanEqual(status: DrawStatus, now: Instant): List<SSList>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SSList s where s.id = :id")
  fun lockById(@Param("id") id: Long): SSList?
}

interface SSPlayerRepository : JpaRepository<SSPlayer, Long> {
  fun findAllBySsListIdOrderByIdAsc(ssListId: Long): List<SSPlayer>

  fun findBySsListIdAndPlayerId(ssListId: Long, playerId: Long): SSPlayer?

  fun countBySsListId(ssListId: Long): Long
}
