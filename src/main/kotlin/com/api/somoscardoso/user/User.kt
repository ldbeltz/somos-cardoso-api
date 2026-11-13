package com.api.somoscardoso.user

import jakarta.persistence.*
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "users")
class User(
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(36)")
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 254) var email: String = "",
    @Column(name = "phone_number", length = 16, unique = true) var phoneNumber: String? = null,
    @Column(name = "password_hash") var passwordHash: String? = null,
    @Column(name = "google_subject", unique = true) var googleSubject: String? = null,
)
