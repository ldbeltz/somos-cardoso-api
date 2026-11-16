package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import com.api.somoscardoso.user.UserRole
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
    val id: UUID,
    val userId: UUID?,
    val name: String,
    val email: String?,
    val phoneNumber: String?,
    val role: UserRole?,
) {
  companion object {
    fun from(player: Player) =
        PlayerView(
            player.id,
            player.user?.id,
            player.name,
            player.user?.email,
            player.phoneNumber,
            player.user?.role,
        )
  }
}
