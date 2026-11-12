package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import jakarta.validation.constraints.*
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String,
    @field:Size(min = 10, max = 72) val password: String,
    @field:NotBlank @field:Size(max = 40) val phoneNumber: String,
)

data class LoginRequest(@field:Email val email: String, val password: String)

data class PhoneRequest(@field:NotBlank @field:Size(max = 40) val phoneNumber: String)

data class PlayerView(
    val id: Long,
    val userId: UUID,
    val name: String,
    val email: String,
    val phoneNumber: String?,
) {
  companion object {
    fun from(player: Player) =
        PlayerView(
            requireNotNull(player.id),
            player.user.id,
            player.name,
            player.user.email,
            player.phoneNumber,
        )
  }
}
