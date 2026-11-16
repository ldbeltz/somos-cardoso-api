package com.api.somoscardoso.player

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface PlayerRepository : JpaRepository<Player, UUID> {
  fun findByUserId(userId: UUID): Player?

  fun findByPhoneNumber(phoneNumber: String): Player?
}
