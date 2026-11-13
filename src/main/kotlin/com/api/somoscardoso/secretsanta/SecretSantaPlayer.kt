package com.api.somoscardoso.secretsanta

import jakarta.persistence.*

@Entity
@Table(
    name = "secret_santa_players",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ss_list_id", "player_id"])],
)
class SecretSantaPlayer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var ssListId: Long = 0,
    var playerId: Long = 0,
    var recipientId: Long? = null,
)
