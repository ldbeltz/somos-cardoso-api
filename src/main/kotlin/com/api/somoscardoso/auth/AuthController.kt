package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val auth: AuthService,
    @Value("\${GOOGLE_CLIENT_ID:}") private val clientId: String,
    @Value("\${GOOGLE_CLIENT_SECRET:}") private val clientSecret: String,
) {
  @GetMapping("/csrf")
  fun csrf(token: CsrfToken) = mapOf("token" to token.token, "headerName" to token.headerName)

  @GetMapping("/providers")
  fun providers() = mapOf("google" to (clientId.isNotBlank() && clientSecret.isNotBlank()))

  @PostMapping("/register")
  fun register(
      @Valid @RequestBody body: RegisterRequest,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): PlayerView = signIn(auth.register(body), request, response)

  @PostMapping("/login")
  fun login(
      @Valid @RequestBody body: LoginRequest,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): PlayerView = signIn(auth.authenticate(body), request, response)

  @GetMapping("/me")
  fun me(authentication: Authentication) = PlayerView.from(auth.current(authentication.name))

  @PutMapping("/me")
  fun update(@Valid @RequestBody body: PhoneRequest, authentication: Authentication) =
      PlayerView.from(auth.updatePhone(authentication.name, body))

  private fun signIn(
      player: Player,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): PlayerView {
    val authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            player.user.id.toString(),
            null,
            listOf(SimpleGrantedAuthority("ROLE_PLAYER")),
        )
    ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response)
    CsrfAuthenticationStrategy(HttpSessionCsrfTokenRepository())
        .onAuthentication(authentication, request, response)
    val context = SecurityContextHolder.createEmptyContext()
    context.authentication = authentication
    SecurityContextHolder.setContext(context)
    HttpSessionSecurityContextRepository().saveContext(context, request, response)
    return PlayerView.from(player)
  }
}

@RestController
@RequestMapping("/api/v1/players")
class PlayerController(private val players: PlayerRepository) {
  @GetMapping fun list() = players.findAll().map(PlayerView::from)
}
