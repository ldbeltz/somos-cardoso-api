package com.api.somoscardoso.player

import com.api.somoscardoso.user.User
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerControllerTests {
  @Test
  fun `should list only players linked to user accounts`() {
    val repository = mockk<PlayerRepository>()
    val linked = Player(id = UUID.randomUUID(), name = "Alice", user = User(email = "a@test"))
    val guest = Player(id = UUID.randomUUID(), name = "Guest")
    every { repository.findAll() } returns listOf(linked, guest)

    val result = PlayerController(repository).list()

    assertEquals(listOf(linked.user!!.id), result.map { it.id })
  }
}
