package com.api.somoscardoso.secretsanta

import org.springframework.data.jpa.repository.JpaRepository

interface SecretSantaWordRepository : JpaRepository<SecretSantaWord, Long>
