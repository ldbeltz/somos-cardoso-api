package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import com.api.somoscardoso.user.UserRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser

class GoogleIdentityServiceTests {
  private val players = mock(PlayerRepository::class.java)
  private val users = mock(UserRepository::class.java)
  private val service = GoogleAccountService(players, users)

  private fun remote(
      email: String = "google@example.com",
      verified: Boolean = true,
  ): DefaultOidcUser {
    val token =
        OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            mapOf(
                "sub" to "google-subject",
                "email" to email,
                "email_verified" to verified,
                "name" to "Google Player",
            ),
        )
    return DefaultOidcUser(listOf(SimpleGrantedAuthority("ROLE_PLAYER")), token)
  }

  @Test
  fun `verified provider creates separate account and player with UUID principal`() {
    `when`(users.saveAndFlush(any(User::class.java))).thenAnswer { it.arguments[0] }
    val principal = service.resolve(remote("NEW@example.com"))
    val userCapture = org.mockito.ArgumentCaptor.forClass(User::class.java)
    verify(users).saveAndFlush(userCapture.capture())
    val playerCapture = org.mockito.ArgumentCaptor.forClass(Player::class.java)
    verify(players).saveAndFlush(playerCapture.capture())
    assertEquals("new@example.com", userCapture.value.email)
    assertEquals(userCapture.value.id.toString(), principal.name)
    assertEquals(userCapture.value.id, playerCapture.value.user!!.id)
    assertNull(userCapture.value.passwordHash)
    assertNull(playerCapture.value.phoneNumber)
    assertEquals("google-subject", userCapture.value.googleSubject)
  }

  @Test
  fun `unverified email rejected before lookup`() {
    assertThrows(OAuth2AuthenticationException::class.java) {
      service.resolve(remote(verified = false))
    }
    verifyNoInteractions(players, users)
  }

  @Test
  fun `matching local email cannot silently link`() {
    `when`(users.findByEmail("google@example.com"))
        .thenReturn(User(email = "google@example.com", passwordHash = "local"))
    assertThrows(OAuth2AuthenticationException::class.java) { service.resolve(remote()) }
    verifyNoInteractions(players)
  }

  @Test
  fun `existing provider subject retains UUID when email changes`() {
    val user = User(email = "original@example.com", googleSubject = "google-subject")
    `when`(users.findByGoogleSubject("google-subject")).thenReturn(user)
    assertEquals(user.id.toString(), service.resolve(remote("changed@example.com")).name)
    verify(users, never()).findByEmail(anyString())
    verifyNoInteractions(players)
  }
}
