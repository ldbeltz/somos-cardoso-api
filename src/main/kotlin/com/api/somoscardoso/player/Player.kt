package com.api.somoscardoso.player

import com.api.somoscardoso.user.User
import jakarta.persistence.*

@Entity
@Table(name = "players")
class Player(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 100) var name: String = "",
    @Column(name = "phone_number", length = 16) var phoneNumber: String? = null,
    @OneToOne(optional = false, cascade = [CascadeType.PERSIST])
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User = User(),
)
