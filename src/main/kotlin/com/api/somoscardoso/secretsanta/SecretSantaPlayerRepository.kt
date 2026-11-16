package com.api.somoscardoso.secretsanta

import org.springframework.data.jpa.repository.JpaRepository

interface SecretSantaPlayerRepository : JpaRepository<SecretSantaPlayer, Long> {
  fun findAllBySsListIdOrderByIdAsc(ssListId: Long): List<SecretSantaPlayer>

  fun findBySsListIdAndPlayerId(ssListId: Long, playerId: java.util.UUID): SecretSantaPlayer?

  fun findBySsListIdAndSecretWord(ssListId: Long, secretWord: String): SecretSantaPlayer?

  fun countBySsListId(ssListId: Long): Long
}
