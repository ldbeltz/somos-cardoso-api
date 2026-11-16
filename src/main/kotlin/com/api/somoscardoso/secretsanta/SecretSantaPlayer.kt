package com.api.somoscardoso.secretsanta

import jakarta.persistence.*
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "secret_santa_players",
    uniqueConstraints =
        [
            UniqueConstraint(columnNames = ["ss_list_id", "player_id"]),
            UniqueConstraint(columnNames = ["ss_list_id", "secret_word"]),
        ],
)
class SecretSantaPlayer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var ssListId: Long = 0,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "player_id", columnDefinition = "char(36)", nullable = false)
    var playerId: UUID = UUID.randomUUID(),
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "recipient_id", columnDefinition = "char(36)")
    var recipientId: UUID? = null,
    @Column(name = "secret_word", length = 64) var secretWord: String? = null,
)
