package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.WhatsAppNotification
import com.api.somoscardoso.messaging.WhatsAppNotificationRepository
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class SecretSantaServiceTest {
  private val now = Instant.parse("2026-12-20T12:00:00Z")
  private val lists = mock(SSListRepository::class.java)
  private val entries = mock(SSPlayerRepository::class.java)
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
          id = 1L,
          name = "Alice",
          user = User(email = "alice@example.test"),
          phoneNumber = "+5511999999999",
      )
  private val bob =
      Player(
          id = 2L,
          name = "Bob",
          user = User(email = "bob@example.test"),
          phoneNumber = "+5511988888888",
      )

  @Test
  fun `rejects insufficient players and draw times not in future`() {
    `when`(players.findByUserId(alice.user.id)).thenReturn(alice)
    for (request in
        listOf(
            CreateSecretSanta("Christmas", now.plusSeconds(60), listOf(1)),
            CreateSecretSanta("Christmas", now, listOf(1, 2)),
        )) {
      assertEquals(
          400,
          assertThrows(ResponseStatusException::class.java) {
                service.create(alice.user.id.toString(), request)
              }
              .statusCode
              .value(),
      )
    }
    verifyNoInteractions(notifications)
  }

  @Test
  fun `due draw persists ring and notifications once`() {
    val list = SSList(id = 9, name = "Christmas", drawAt = now.minusSeconds(60), creatorId = 1)
    val members =
        listOf(
            SSPlayer(id = 11, ssListId = 9, playerId = 1),
            SSPlayer(id = 12, ssListId = 9, playerId = 2),
        )
    `when`(lists.lockById(9)).thenReturn(list)
    `when`(entries.findAllBySsListIdOrderByIdAsc(9)).thenReturn(members)
    `when`(players.findAllById(listOf(1L, 2L))).thenReturn(listOf(alice, bob))
    assertTrue(service.drawIfDue(9))
    assertEquals(2L, members[0].recipientId)
    assertEquals(1L, members[1].recipientId)
    assertEquals(DrawStatus.DRAWN, list.status)
    assertEquals(now, list.drawnAt)
    assertFalse(service.drawIfDue(9))
    verify(notifications, times(2)).save(any(WhatsAppNotification::class.java))
  }

  @Test
  fun `does not draw before scheduled instant`() {
    `when`(lists.lockById(9)).thenReturn(SSList(id = 9, drawAt = now.plusSeconds(1)))
    assertFalse(service.drawIfDue(9))
    verifyNoInteractions(entries, notifications)
  }

  @Test
  fun `only registered participants can see detail`() {
    `when`(players.findByUserId(alice.user.id)).thenReturn(alice)
    `when`(lists.findById(9)).thenReturn(Optional.of(SSList(id = 9)))
    `when`(entries.findAllBySsListIdOrderByIdAsc(9))
        .thenReturn(listOf(SSPlayer(id = 12, ssListId = 9, playerId = 2)))
    assertEquals(
        403,
        assertThrows(ResponseStatusException::class.java) {
              service.detail(9, alice.user.id.toString())
            }
            .statusCode
            .value(),
    )
  }

  @Test
  fun `joining and leaving lock out when draw is due`() {
    `when`(lists.lockById(9)).thenReturn(SSList(id = 9, drawAt = now))
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.join(9, alice.user.id.toString())
            }
            .statusCode
            .value(),
    )
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.leave(9, alice.user.id.toString())
            }
            .statusCode
            .value(),
    )
  }

  @Test
  fun `creator cannot leave and a draw cannot fall below two players`() {
    `when`(lists.lockById(9))
        .thenReturn(SSList(id = 9, drawAt = now.plusSeconds(60), creatorId = 1))
    `when`(players.findByUserId(alice.user.id)).thenReturn(alice)
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.leave(9, alice.user.id.toString())
            }
            .statusCode
            .value(),
    )
    `when`(players.findByUserId(bob.user.id)).thenReturn(bob)
    `when`(entries.findBySsListIdAndPlayerId(9, 2))
        .thenReturn(SSPlayer(id = 12, ssListId = 9, playerId = 2))
    `when`(entries.countBySsListId(9)).thenReturn(2)
    assertEquals(
        409,
        assertThrows(ResponseStatusException::class.java) {
              service.leave(9, bob.user.id.toString())
            }
            .statusCode
            .value(),
    )
  }
}
