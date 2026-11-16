package com.api.somoscardoso.secretsanta

import jakarta.persistence.*

@Entity
@Table(
    name = "secret_santa_words",
    uniqueConstraints = [UniqueConstraint(columnNames = ["locale", "word"])],
)
class SecretSantaWord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 8) var locale: String = "",
    @Column(nullable = false, length = 64) var word: String = "",
)
