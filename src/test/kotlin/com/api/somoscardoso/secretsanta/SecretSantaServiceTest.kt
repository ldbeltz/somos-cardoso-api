package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.WhatsAppNotification
import com.api.somoscardoso.messaging.WhatsAppNotificationRepository
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class SecretSantaServiceTest {
  private val now = Instant.parse("2026-12-20T12:00:00Z")
  private val aliceId = UUID.fromString("10000000-0000-0000-0000-000000000001")
  private val bobId = UUID.fromString("20000000-0000-0000-0000-000000000002")
  private val lists = mock(SecretSantaListRepository::class.java)
  private val entries = mock(SecretSantaPlayerRepository::class.java)
  private val players = mock(PlayerRepository::class.java)
  private val notifications = mock(WhatsAppNotificationRepository::class.java)
  private val service =
      SecretSantaService(
          lists,
          entries,
          players,
          notifications,
          CircularDraw(),
          Clock.fixed(now, ZoneOffset.UTC),
      )
  private val alice =
      Player(
          id = aliceId,
          name = "Alice",
          user = User(email = "alice@example.test"),
          phoneNumber = "+5511999999999",
      )
  private val bob =
      Player(
          id = bobId,
          name = "Bob",
          user = User(email = "bob@example.test"),
          phoneNumber = "+5511988888888",
      )

  @Test
  fun `rejects insufficient players and draw times not in future`() {
    `when`(players.findByUserId(alice.user!!.id)).thenReturn(alice)
    for (request in
        listOf(
            CreateSecretSanta("Christmas", now.plusSeconds(60), listOf(UUID.randomUUID())),
            CreateSecretSanta("Christmas", now, listOf(UUID.randomUUID(), UUID.randomUUID())),
        )) {
      assertEquals(
          400,
          assertThrows(ResponseStatusException::class.java) {
                service.create(alice.user!!.id.toString(), request)
              }
              .statusCode
              .value(),
      )
    }
    verifyNoInteractions(notifications)
  }

  @Test
  fun `due draw persists ring and notifications once`() {
    val list =
        SecretSantaList(
            id = 9,
            name = "Christmas",
            drawAt = now.minusSeconds(60),
            creatorId = aliceId,
        )
    val members =
        listOf(
            SecretSantaPlayer(id = 11, ssListId = 9, playerId = aliceId),
            SecretSantaPlayer(id = 12, ssListId = 9, playerId = bobId),
        )
    `when`(lists.lockById(9)).thenReturn(list)
    `when`(entries.findAllBySsListIdOrderByIdAsc(9)).thenReturn(members)
    `when`(players.findAllById(listOf(aliceId, bobId))).thenReturn(listOf(alice, bob))
    assertTrue(service.drawIfDue(9))
    assertEquals(bobId, members[0].recipientId)
    assertEquals(aliceId, members[1].recipientId)
    assertEquals(DrawStatus.DRAWN, list.status)
    assertEquals(now, list.drawnAt)
    assertFalse(service.drawIfDue(9))
    verify(notifications, times(2)).save(any(WhatsAppNotification::class.java))
  }

  @Test
  fun `does not draw before scheduled instant`() {
    `when`(lists.lockById(9)).thenReturn(SecretSantaList(id = 9, drawAt = now.plusSeconds(1)))
    assertFalse(service.drawIfDue(9))
    verifyNoInteractions(entries, notifications)
  }

  @Test
  fun `only registered participants can see detail`() {
    `when`(players.findByUserId(alice.user!!.id)).thenReturn(alice)
    `when`(lists.lockById(9)).thenReturn(SecretSantaList(id = 9))
    `when`(entries.findAllBySsListIdOrderByIdAsc(9))
        .thenReturn(listOf(SecretSantaPlayer(id = 12, ssListId = 9, playerId = bobId)))
    assertEquals(
        403,
        assertThrows(ResponseStatusException::class.java) {
              service.detail(9, alice.user!!.id.toString())
            }
            .statusCode
            .value(),
    )
  }

  @Test
  fun `joining and leaving lock out when draw is due`() {
    `when`(lists.lockById(9)).thenReturn(SecretSantaList(id = 9, drawAt = now))
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.join(9, alice.user!!.id.toString())
            }
            .statusCode
            .value(),
    )
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.leave(9, alice.user!!.id.toString())
            }
            .statusCode
            .value(),
    )
  }

  @Test
  fun `invite join rejects draws that are no longer scheduled with a clear message`() {
    for (status in listOf(DrawStatus.DRAWN, DrawStatus.NOT_DRAWN)) {
      val list =
          SecretSantaList(
              id = 9,
              drawAt = now.plusSeconds(60),
              status = status,
              inviteToken = "closed-token",
          )
      `when`(lists.findByInviteToken("closed-token")).thenReturn(list)
      `when`(lists.lockById(9)).thenReturn(list)

      val error =
          assertThrows(ResponseStatusException::class.java) {
            service.joinByInvite("closed-token", InviteRegistration("Guest", "+5511977777777"))
          }

      assertEquals(409, error.statusCode.value())
      assertEquals(
          "This invite is closed. Players can only join a scheduled draw before its draw time.",
          error.reason,
      )
    }
    verifyNoInteractions(players, entries, notifications)
  }

  @Test
  fun `creator and participants may leave before a draw`() {
    `when`(lists.lockById(9))
        .thenReturn(SecretSantaList(id = 9, drawAt = now.plusSeconds(60), creatorId = aliceId))
    `when`(players.findByUserId(alice.user!!.id)).thenReturn(alice)
    `when`(entries.findBySsListIdAndPlayerId(9, aliceId))
        .thenReturn(SecretSantaPlayer(id = 11, ssListId = 9, playerId = aliceId))
    assertDoesNotThrow { service.leave(9, alice.user!!.id.toString()) }
    verify(entries).delete(any(SecretSantaPlayer::class.java))
    `when`(lists.lockById(9))
        .thenReturn(SecretSantaList(id = 9, drawAt = now.plusSeconds(60), creatorId = aliceId))
    `when`(entries.findBySsListIdAndPlayerId(9, bobId))
        .thenReturn(SecretSantaPlayer(id = 12, ssListId = 9, playerId = bobId))
    `when`(players.findByUserId(bob.user!!.id)).thenReturn(bob)
    assertDoesNotThrow { service.leave(9, bob.user!!.id.toString()) }
    verify(entries, times(2)).delete(any(SecretSantaPlayer::class.java))
  }
}
