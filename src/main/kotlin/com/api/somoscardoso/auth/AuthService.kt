package com.api.somoscardoso.auth

import com.api.somoscardoso.player.PhoneNumbers
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import com.api.somoscardoso.user.UserRepository
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val players: PlayerRepository,
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
) {
  @Transactional
  fun register(request: RegisterRequest): Player {
    val email = request.email.trim().lowercase()
    val phone = PhoneNumbers.normalize(request.phoneNumber)
    if (users.findByEmail(email) != null)
        throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "An account with this email already exists",
        )
    if (request.password.toByteArray(Charsets.UTF_8).size > 72)
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Password must fit within 72 UTF-8 bytes",
        )
    val existing = players.findByPhoneNumber(phone)
    if (existing?.user != null)
        throw ResponseStatusException(HttpStatus.CONFLICT, "This phone number is already in use")
    val user =
        users.saveAndFlush(
            User(
                email = email,
                phoneNumber = phone,
                passwordHash = encoder.encode(request.password),
            )
        )
    if (existing != null) {
      existing.name = request.name.trim()
      existing.user = user
      return players.saveAndFlush(existing)
    }
    return players.saveAndFlush(
        Player(name = request.name.trim(), phoneNumber = phone, user = user)
    )
  }

  fun authenticate(request: LoginRequest): Player {
    val user = users.findByEmail(request.email.trim().lowercase())
    if (
        user?.passwordHash == null ||
            request.password.toByteArray(Charsets.UTF_8).size > 72 ||
            !encoder.matches(request.password, user.passwordHash)
    )
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
    return current(user.id.toString())
  }

  fun current(subject: String): Player {
    val userId =
        try {
          UUID.fromString(subject)
        } catch (_: IllegalArgumentException) {
          throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in")
        }
    return players.findByUserId(userId)
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in")
  }

  @Transactional
  fun updatePhone(subject: String, request: PhoneRequest): Player {
    val player = current(subject)
    val phone = PhoneNumbers.normalize(request.phoneNumber)
    val other = players.findByPhoneNumber(phone)
    if (other != null && other.id != player.id)
        throw ResponseStatusException(HttpStatus.CONFLICT, "This phone number is already in use")
    player.phoneNumber = phone
    requireNotNull(player.user).phoneNumber = phone
    return players.save(player)
  }
}
