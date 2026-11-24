package com.api.somoscardoso.secretsanta

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.Principal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecretSantaControllerTests {
  private val service = mockk<SecretSantaService>(relaxed = true)
  private val controller = SecretSantaController(service)
  private val principal = Principal { "alice-subject" }
  private val playerId = UUID.fromString("10000000-0000-0000-0000-000000000001")
  private val draw =
      SecretSantaDetail(
          9,
          "Christmas",
          Instant.parse("2026-12-20T12:00:00Z"),
          DrawStatus.SCHEDULED,
          playerId,
          1,
          true,
          listOf(PlayerName(playerId, "Alice")),
          null,
          "alecrim",
          null,
          "invite-token",
      )

  @Test
  fun `should delegate every draw endpoint with its request values`() {
    val registration = InviteRegistration("Guest", "+5511999999999")
    val create = CreateSecretSanta("Christmas", draw.drawAt, listOf(playerId))
    val change = ChangeDrawDate(draw.drawAt)
    val add = AddSecretSantaPlayer(playerId)
    val reveal = RevealRecipientRequest("alecrim")
    val invite = InviteInfo(9, "Christmas", draw.drawAt, DrawStatus.SCHEDULED)
    val recipient = PlayerName(playerId, "Alice")
    every { service.inviteInfo("invite-token") } returns invite
    every { service.joinByInvite("invite-token", registration) } returns invite
    every { service.create("alice-subject", create) } returns draw
    every { service.join(9, "alice-subject") } returns draw
    every { service.detail(9, "alice-subject") } returns draw
    every { service.revealRecipient(9, reveal) } returns recipient

    assertEquals(invite, controller.inviteInfo("invite-token"))
    assertEquals(invite, controller.joinByInvite("invite-token", registration))
    controller.all(principal)
    assertEquals(draw, controller.create(principal, create))
    controller.changeDrawDate(9, change)
    controller.addPlayer(9, add)
    controller.removePlayer(9, playerId)
    controller.delete(9)
    assertEquals(draw, controller.detail(9, principal))
    assertEquals(recipient, controller.revealRecipient(9, reveal))
    assertEquals(draw, controller.join(9, principal))
    controller.leave(9, principal)

    verify { service.all("alice-subject") }
    verify { service.changeDrawDate(9, change) }
    verify { service.addPlayer(9, playerId) }
    verify { service.removePlayer(9, playerId) }
    verify { service.delete(9) }
    verify { service.leave(9, "alice-subject") }
  }
}
