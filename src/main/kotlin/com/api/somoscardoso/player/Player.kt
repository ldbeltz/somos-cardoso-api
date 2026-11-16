package com.api.somoscardoso.player

import com.api.somoscardoso.user.User
import jakarta.persistence.*
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "players", uniqueConstraints = [UniqueConstraint(columnNames = ["phone_number"])])
class Player(
    @JdbcTypeCode(SqlTypes.CHAR)
    @Id
    @Column(columnDefinition = "char(36)")
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 100) var name: String = "",
    @Column(name = "phone_number", length = 16, unique = true) var phoneNumber: String? = null,
    @OneToOne(optional = true, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinColumn(name = "user_id", unique = true)
    var user: User? = null,
)
