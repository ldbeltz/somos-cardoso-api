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
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val encoder: PasswordEncoder,
) {
  @Transactional
  fun register(request: RegisterRequest): User {
    val email = request.email.trim().lowercase()
    val phone = PhoneNumbers.normalize(request.phoneNumber)
    if (userRepository.findByEmail(email) != null)
        throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "An account with this email already exists",
        )
    if (request.password.toByteArray(Charsets.UTF_8).size > 72)
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Password must fit within 72 UTF-8 bytes",
        )
    val existingPlayer = playerRepository.findByPhoneNumber(phone)
    if (existingPlayer?.user != null)
        throw ResponseStatusException(HttpStatus.CONFLICT, "This phone number is already in use")
    val user =
        userRepository.saveAndFlush(
            User(
                name = request.name.trim(),
                email = email,
                phoneNumber = phone,
                passwordHash = encoder.encode(request.password),
            )
        )
    if (existingPlayer != null) {
      existingPlayer.name = request.name.trim()
      existingPlayer.user = user
      playerRepository.save(existingPlayer)
      return user
    }
    playerRepository.save(Player(name = request.name.trim(), phoneNumber = phone, user = user))
    return user
  }

  fun authenticate(request: LoginRequest): User {
    val user = userRepository.findByEmail(request.email.trim().lowercase())
    if (
        user?.passwordHash == null ||
            request.password.toByteArray(Charsets.UTF_8).size > 72 ||
            !encoder.matches(request.password, user.passwordHash)
    )
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
    return current(user.id.toString())
  }

  fun current(subject: String): User {
    val userId =
        try {
          UUID.fromString(subject)
        } catch (_: IllegalArgumentException) {
          throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in")
        }
    return userRepository.findById(userId).orElseThrow {
      ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in")
    }
  }

  @Transactional
  fun updatePhone(subject: String, request: PhoneRequest): User {
    val user = current(subject)
    val phone = PhoneNumbers.normalize(request.phoneNumber)
    val otherPlayer = playerRepository.findByPhoneNumber(phone)
    if (otherPlayer != null && otherPlayer.user?.id != user.id)
        throw ResponseStatusException(HttpStatus.CONFLICT, "This phone number is already in use")
    val userPlayer = playerRepository.findByUserId(user.id)
    userPlayer?.phoneNumber = phone
    user.phoneNumber = phone
    userRepository.saveAndFlush(user)
    if (userPlayer != null) {
      playerRepository.save(userPlayer)
    }
    return user
  }
}
