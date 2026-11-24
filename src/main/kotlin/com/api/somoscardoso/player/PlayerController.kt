package com.api.somoscardoso.player

import com.api.somoscardoso.auth.UserView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/players")
class PlayerController(private val players: PlayerRepository) {
  @GetMapping fun list() = players.findAll().mapNotNull { it.user?.let(UserView::from) }
}
