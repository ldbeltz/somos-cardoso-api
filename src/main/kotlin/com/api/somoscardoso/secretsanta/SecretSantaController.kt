package com.api.somoscardoso.secretsanta

import java.security.Principal
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/secret-santas")
class SecretSantaController(private val service: SecretSantaService) {
  @GetMapping fun all(principal: Principal) = service.all(principal.name)

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun create(principal: Principal, @RequestBody request: CreateSecretSanta) =
      service.create(principal.name, request)

  @GetMapping("/{id}")
  fun detail(@PathVariable id: Long, principal: Principal) = service.detail(id, principal.name)

  @PostMapping("/{id}/join")
  fun join(@PathVariable id: Long, principal: Principal) = service.join(id, principal.name)

  @DeleteMapping("/{id}/players/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun leave(@PathVariable id: Long, principal: Principal) = service.leave(id, principal.name)
}
