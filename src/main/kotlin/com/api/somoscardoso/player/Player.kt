package com.api.somoscardoso.player

import com.api.somoscardoso.user.User
import jakarta.persistence.*

@Entity
@Table(name = "players", uniqueConstraints = [UniqueConstraint(columnNames = ["phone_number"])])
class Player(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 100) var name: String = "",
    @Column(name = "phone_number", length = 16, unique = true) var phoneNumber: String? = null,
    @OneToOne(optional = true, cascade = [CascadeType.PERSIST])
    @JoinColumn(name = "user_id", unique = true)
    var user: User? = null,
)
