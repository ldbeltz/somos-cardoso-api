package com.api.somoscardoso.secretsanta

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "secret_santa_lists")
class SecretSantaList(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(length = 120) var name: String = "",
    var drawAt: Instant = Instant.EPOCH,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "creator_id", columnDefinition = "char(36)", nullable = false)
    var creatorId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    var status: DrawStatus = DrawStatus.SCHEDULED,
    var drawnAt: Instant? = null,
    @Column(nullable = false, unique = true, length = 64) var inviteToken: String = "",
)
