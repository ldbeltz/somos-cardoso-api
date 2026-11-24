package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import com.api.somoscardoso.user.UserRepository
import java.util.Optional
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException

class AuthServiceTests {
  private val players = mock(PlayerRepository::class.java)
  private val users = mock(UserRepository::class.java)
  private val encoder = mock(PasswordEncoder::class.java)
  private val service = AuthService(players, users, encoder)

  @Test
  fun `register normalizes user fields and creates linked player`() {
    `when`(encoder.encode("long enough password")).thenReturn("hashed")
    `when`(users.saveAndFlush(any(User::class.java))).thenAnswer { it.arguments[0] }

    val result =
        service.register(
            RegisterRequest(
                "  Ana Silva  ",
                " ANA@Example.COM ",
                "long enough password",
                "(11) 99999-9999",
            )
        )

    assertEquals("Ana Silva", result.name)
    assertEquals("ana@example.com", result.email)
    assertEquals("+5511999999999", result.phoneNumber)
    assertEquals("hashed", result.passwordHash)
    val savedPlayer = ArgumentCaptor.forClass(Player::class.java)
    verify(players).save(savedPlayer.capture())
    assertEquals("Ana Silva", savedPlayer.value.name)
    assertEquals(result.phoneNumber, savedPlayer.value.phoneNumber)
    assertEquals(result.id, savedPlayer.value.user?.id)
  }

  @Test
  fun `register links an existing unclaimed player`() {
    val existingPlayer = Player(name = "Guest", phoneNumber = "+5511999999999")
    `when`(players.findByPhoneNumber("+5511999999999")).thenReturn(existingPlayer)
    `when`(encoder.encode("long enough password")).thenReturn("hashed")
    `when`(users.saveAndFlush(any(User::class.java))).thenAnswer { it.arguments[0] }

    val result =
        service.register(
            RegisterRequest("New Name", "new@example.com", "long enough password", "11999999999")
        )

    assertSame(result, existingPlayer.user)
    assertEquals("New Name", existingPlayer.name)
    verify(players).save(existingPlayer)
  }

  @Test
  fun `register rejects duplicate email before other writes`() {
    `when`(users.findByEmail("taken@example.com")).thenReturn(User(email = "taken@example.com"))

    val error =
        assertThrows(ResponseStatusException::class.java) {
          service.register(
              RegisterRequest("Name", " TAKEN@example.com ", "long enough password", "11999999999")
          )
        }

    assertEquals(HttpStatus.CONFLICT, error.statusCode)
    verifyNoInteractions(players)
    verify(users, never()).saveAndFlush(any(User::class.java))
  }

  @Test
  fun `register rejects password exceeding bcrypt byte limit`() {
    val error =
        assertThrows(ResponseStatusException::class.java) {
          service.register(
              RegisterRequest("Name", "new@example.com", "é".repeat(37), "11999999999")
          )
        }

    assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    verify(encoder, never()).encode(anyString())
    verify(users, never()).saveAndFlush(any(User::class.java))
  }

  @Test
  fun `register rejects phone already linked to an account`() {
    `when`(players.findByPhoneNumber("+5511999999999"))
        .thenReturn(
            Player(phoneNumber = "+5511999999999", user = User(email = "existing@example.com"))
        )

    val error =
        assertThrows(ResponseStatusException::class.java) {
          service.register(
              RegisterRequest("Name", "new@example.com", "long enough password", "11999999999")
          )
        }

    assertEquals(HttpStatus.CONFLICT, error.statusCode)
    verify(users, never()).saveAndFlush(any(User::class.java))
  }

  @Test
  fun `authenticate normalizes email verifies password and reloads user`() {
    val user = User(email = "ana@example.com", passwordHash = "hashed")
    `when`(users.findByEmail("ana@example.com")).thenReturn(user)
    `when`(encoder.matches("secret", "hashed")).thenReturn(true)
    `when`(users.findById(user.id)).thenReturn(Optional.of(user))

    assertSame(user, service.authenticate(LoginRequest(" ANA@Example.com ", "secret")))

    verify(users).findById(user.id)
  }

  @Test
  fun `authenticate rejects missing password hash wrong password and oversized password`() {
    `when`(users.findByEmail("missing@example.com")).thenReturn(null)
    `when`(users.findByEmail("google@example.com")).thenReturn(User(email = "google@example.com"))
    `when`(users.findByEmail("local@example.com"))
        .thenReturn(User(email = "local@example.com", passwordHash = "hashed"))
    `when`(encoder.matches("wrong", "hashed")).thenReturn(false)

    listOf(
            LoginRequest("missing@example.com", "anything"),
            LoginRequest("google@example.com", "anything"),
            LoginRequest("local@example.com", "wrong"),
            LoginRequest("local@example.com", "x".repeat(73)),
        )
        .forEach { request ->
          val error =
              assertThrows(ResponseStatusException::class.java) { service.authenticate(request) }
          assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
        }
  }

  @Test
  fun `current resolves user UUID`() {
    val user = User()
    `when`(users.findById(user.id)).thenReturn(Optional.of(user))

    assertSame(user, service.current(user.id.toString()))
  }

  @Test
  fun `current rejects malformed and unknown subjects`() {
    val malformed =
        assertThrows(ResponseStatusException::class.java) { service.current("not-a-uuid") }
    assertEquals(HttpStatus.UNAUTHORIZED, malformed.statusCode)
    val unknown =
        assertThrows(ResponseStatusException::class.java) {
          service.current(java.util.UUID.randomUUID().toString())
        }
    assertEquals(HttpStatus.UNAUTHORIZED, unknown.statusCode)
  }

  @Test
  fun `updatePhone updates user and linked player`() {
    val user = User(phoneNumber = "+5511888888888")
    val player = Player(name = user.name, phoneNumber = user.phoneNumber, user = user)
    `when`(users.findById(user.id)).thenReturn(Optional.of(user))
    `when`(players.findByPhoneNumber("+5521999999999")).thenReturn(null)
    `when`(players.findByUserId(user.id)).thenReturn(player)
    `when`(users.saveAndFlush(user)).thenReturn(user)

    val result = service.updatePhone(user.id.toString(), PhoneRequest("21 99999-9999"))

    assertSame(user, result)
    assertEquals("+5521999999999", user.phoneNumber)
    assertEquals(user.phoneNumber, player.phoneNumber)
    verify(users).saveAndFlush(user)
    verify(players).save(player)
  }

  @Test
  fun `updatePhone permits same linked number and handles users without a player`() {
    val user = User(phoneNumber = "+5511999999999")
    val player = Player(phoneNumber = user.phoneNumber, user = user)
    `when`(users.findById(user.id)).thenReturn(Optional.of(user))
    `when`(players.findByPhoneNumber(user.phoneNumber!!)).thenReturn(player)
    `when`(players.findByUserId(user.id)).thenReturn(null)
    `when`(users.saveAndFlush(user)).thenReturn(user)

    service.updatePhone(user.id.toString(), PhoneRequest("11999999999"))

    assertEquals("+5511999999999", user.phoneNumber)
    verify(players, never()).save(any(Player::class.java))
  }

  @Test
  fun `updatePhone rejects number owned by another player`() {
    val user = User()
    val other = Player(phoneNumber = "+5521999999999", user = User())
    `when`(users.findById(user.id)).thenReturn(Optional.of(user))
    `when`(players.findByPhoneNumber("+5521999999999")).thenReturn(other)

    val error =
        assertThrows(ResponseStatusException::class.java) {
          service.updatePhone(user.id.toString(), PhoneRequest("21999999999"))
        }

    assertEquals(HttpStatus.CONFLICT, error.statusCode)
    verify(users, never()).saveAndFlush(any(User::class.java))
  }
}
