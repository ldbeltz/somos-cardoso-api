package com.api.somoscardoso.secretsanta

import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/secret-santas")
class SecretSantaController(private val service: SecretSantaService) {
  @GetMapping("/invites/{token}")
  fun inviteInfo(@PathVariable token: String) = service.inviteInfo(token)

  @PostMapping("/invites/{token}/join")
  @ResponseStatus(HttpStatus.CREATED)
  fun joinByInvite(@PathVariable token: String, @RequestBody request: InviteRegistration) =
      service.joinByInvite(token, request)

  @GetMapping fun all(principal: Principal) = service.all(principal.name)

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun create(principal: Principal, @RequestBody request: CreateSecretSanta) =
      service.create(principal.name, request)

  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun changeDrawDate(@PathVariable id: Long, @RequestBody request: ChangeDrawDate) =
      service.changeDrawDate(id, request)

  @PostMapping("/{id}/players")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun addPlayer(@PathVariable id: Long, @RequestBody request: AddSecretSantaPlayer) =
      service.addPlayer(id, request.playerId)

  @DeleteMapping("/{id}/players/{playerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun removePlayer(@PathVariable id: Long, @PathVariable playerId: UUID) =
      service.removePlayer(id, playerId)

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun delete(@PathVariable id: Long) = service.delete(id)

  @GetMapping("/{id}")
  fun detail(@PathVariable id: Long, principal: Principal) = service.detail(id, principal.name)

  @PostMapping("/{id}/reveal")
  fun revealRecipient(
      @PathVariable id: Long,
      @RequestBody request: RevealRecipientRequest,
  ) = service.revealRecipient(id, request)

  @PostMapping("/{id}/join")
  fun join(@PathVariable id: Long, principal: Principal) = service.join(id, principal.name)

  @DeleteMapping("/{id}/players/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun leave(@PathVariable id: Long, principal: Principal) = service.leave(id, principal.name)
}
