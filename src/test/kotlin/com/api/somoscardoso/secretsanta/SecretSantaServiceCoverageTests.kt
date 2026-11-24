package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.NotificationStatus
import com.api.somoscardoso.messaging.NotificationType
import com.api.somoscardoso.messaging.WhatsAppNotification
import com.api.somoscardoso.messaging.WhatsAppNotificationRepository
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class SecretSantaServiceCoverageTests {
  private val now = Instant.parse("2026-12-20T12:00:00Z")
  private val alice =
      Player(
          id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
          name = "Alice",
          phoneNumber = "+5511999999999",
          user = User(email = "alice@example.test"),
      )
  private val bob =
      Player(
          id = UUID.fromString("20000000-0000-0000-0000-000000000002"),
          name = "Bob",
          phoneNumber = "+5511888888888",
          user = User(email = "bob@example.test"),
      )

  private inner class Harness {
    val lists = mockk<SecretSantaListRepository>(relaxed = true)
    val entries = mockk<SecretSantaPlayerRepository>(relaxed = true)
    val players = mockk<PlayerRepository>(relaxed = true)
    val notifications = mockk<WhatsAppNotificationRepository>(relaxed = true)
    val words = mockk<SecretSantaWordRepository>(relaxed = true)
    val service =
        SecretSantaService(
            lists,
            entries,
            players,
            notifications,
            CircularDraw(),
            Clock.fixed(now, ZoneOffset.UTC),
            words,
        )

    fun signedIn() {
      every { players.findByUserId(alice.user!!.id) } returns alice
    }

    fun openList() =
        SecretSantaList(
            id = 9,
            name = "Christmas",
            drawAt = now.plusSeconds(600),
            creatorId = alice.id,
            inviteToken = "invite-token",
        )
  }

  @Test
  fun `should sort and include only lists the player can access`() {
    val h = Harness()
    h.signedIn()
    val earlier =
        h.openList().apply {
          id = 1
          drawAt = now.plusSeconds(60)
        }
    val mine =
        h.openList().apply {
          id = 2
          drawAt = now.plusSeconds(30)
        }
    val outsider =
        h.openList().apply {
          id = 3
          creatorId = bob.id
        }
    every { h.lists.findAll() } returns listOf(earlier, mine, outsider)
    every { h.entries.findBySsListIdAndPlayerId(1, alice.id) } returns
        SecretSantaPlayer(id = 11, ssListId = 1, playerId = alice.id)
    every { h.entries.findBySsListIdAndPlayerId(2, alice.id) } returns null
    every { h.entries.findBySsListIdAndPlayerId(3, alice.id) } returns null
    every { h.entries.findAllBySsListIdOrderByIdAsc(1) } returns
        listOf(SecretSantaPlayer(id = 11, ssListId = 1, playerId = alice.id))

    val result = h.service.all(alice.user!!.id.toString())

    assertEquals(listOf(2L, 1L), result.map { it.id })
    assertFalse(result.first().isParticipant)
    assertTrue(result.last().isParticipant)
  }

  @Test
  fun `should reject invalid creation names, duplicate players, and oversized groups`() {
    val h = Harness()
    h.signedIn()
    val future = now.plusSeconds(60)
    val requests =
        listOf(
            CreateSecretSanta("  ", future),
            CreateSecretSanta("x".repeat(121), future),
            CreateSecretSanta("draw", future, List(101) { UUID.randomUUID() }),
            CreateSecretSanta("draw", future, listOf(bob.id, bob.id)),
        )

    requests.forEach { request ->
      assertEquals(
          HttpStatus.BAD_REQUEST,
          assertThrows(ResponseStatusException::class.java) {
                h.service.create(alice.user!!.id.toString(), request)
              }
              .statusCode,
      )
    }
  }

  @Test
  fun `should update draw date and reschedule notices that were not sent`() {
    val h = Harness()
    val list = h.openList()
    val pendingMember = SecretSantaPlayer(id = 11, ssListId = 9, playerId = alice.id)
    val sentMember = SecretSantaPlayer(id = 12, ssListId = 9, playerId = bob.id)
    val pending =
        WhatsAppNotification(
            id = 21,
            ssPlayerId = 11,
            type = NotificationType.REGISTRATION,
            scheduledDrawAt = list.drawAt,
        )
    val sent =
        WhatsAppNotification(
            id = 22,
            ssPlayerId = 12,
            type = NotificationType.REGISTRATION,
            status = NotificationStatus.SENT,
        )
    every { h.lists.lockById(9) } returns list
    every { h.entries.findAllBySsListIdOrderByIdAsc(9) } returns listOf(pendingMember, sentMember)
    every { h.notifications.findBySsPlayerIdAndType(11, NotificationType.REGISTRATION) } returns
        pending
    every { h.notifications.findBySsPlayerIdAndType(12, NotificationType.REGISTRATION) } returns
        sent
    every { h.lists.save(list) } returns list
    every { h.notifications.save(pending) } returns pending
    val newDate = now.plusSeconds(1800)

    h.service.changeDrawDate(9, ChangeDrawDate(newDate))

    assertEquals(newDate, list.drawAt)
    assertEquals(newDate, pending.scheduledDrawAt)
    verify(exactly = 1) { h.notifications.save(pending) }
    verify(exactly = 0) { h.notifications.save(sent) }
  }

  @Test
  fun `should add a player with an unused secret word`() {
    val h = Harness()
    val list = h.openList()
    val membership = SecretSantaPlayer(id = 15, ssListId = 9, playerId = bob.id)
    every { h.lists.lockById(9) } returns list
    every { h.players.findById(bob.id) } returns Optional.of(bob)
    every { h.entries.findBySsListIdAndPlayerId(9, bob.id) } returns null
    every { h.entries.countBySsListId(9) } returns 0
    every { h.entries.findAllBySsListIdOrderByIdAsc(9) } returns emptyList()
    every { h.entries.saveAndFlush(any()) } answers
        {
          firstArg<SecretSantaPlayer>().apply { id = membership.id }
        }
    every { h.notifications.save(any()) } answers { firstArg() }

    h.service.addPlayer(9, bob.id)

    verify {
      h.entries.saveAndFlush(match { it.playerId == bob.id && !it.secretWord.isNullOrBlank() })
    }
    verify {
      h.notifications.save(
          match { it.ssPlayerId == 15L && it.type == NotificationType.REGISTRATION }
      )
    }
  }

  @Test
  fun `should remove a player and their notices`() {
    val h = Harness()
    val list = h.openList()
    val member = SecretSantaPlayer(id = 31, ssListId = 9, playerId = bob.id)
    every { h.lists.lockById(9) } returns list
    every { h.entries.findBySsListIdAndPlayerId(9, bob.id) } returns member

    h.service.removePlayer(9, bob.id)

    verify { h.notifications.deleteAllBySsPlayerId(31) }
    verify { h.entries.delete(member) }
  }

  @Test
  fun `should delete every member before deleting the draw`() {
    val h = Harness()
    every { h.lists.lockById(9) } returns h.openList()
    val members =
        listOf(
            SecretSantaPlayer(id = 31, ssListId = 9, playerId = alice.id),
            SecretSantaPlayer(id = 32, ssListId = 9, playerId = bob.id),
        )
    every { h.entries.findAllBySsListIdOrderByIdAsc(9) } returns members

    h.service.delete(9)

    verify(exactly = 2) { h.notifications.deleteAllBySsPlayerId(any()) }
    verify(exactly = 2) { h.entries.delete(any<SecretSantaPlayer>()) }
    verify { h.lists.deleteById(9) }
  }

  @Test
  fun `should reveal the assigned recipient using a case insensitive secret word`() {
    val h = Harness()
    val list = h.openList().apply { status = DrawStatus.DRAWN }
    val membership =
        SecretSantaPlayer(
            id = 31,
            ssListId = 9,
            playerId = alice.id,
            recipientId = bob.id,
            secretWord = "alecrim",
        )
    every { h.lists.findById(9) } returns Optional.of(list)
    every { h.entries.findBySsListIdAndSecretWord(9, "alecrim") } returns membership
    every { h.players.findById(bob.id) } returns Optional.of(bob)

    val result = h.service.revealRecipient(9, RevealRecipientRequest(" ALECRIM "))

    assertEquals(PlayerName(bob.id, bob.name), result)
  }

  @Test
  fun `should mark a due draw with fewer than two members as not drawn`() {
    val h = Harness()
    val list = h.openList().apply { drawAt = now }
    val member = SecretSantaPlayer(id = 31, ssListId = 9, playerId = alice.id)
    val registration =
        WhatsAppNotification(id = 41, ssPlayerId = 31, type = NotificationType.REGISTRATION)
    every { h.lists.lockById(9) } returns list
    every { h.entries.findAllBySsListIdOrderByIdAsc(9) } returns listOf(member)
    every { h.notifications.findBySsPlayerIdAndType(31, NotificationType.REGISTRATION) } returns
        registration
    every { h.notifications.save(registration) } returns registration
    every { h.lists.save(list) } returns list

    assertTrue(h.service.drawIfDue(9))

    assertEquals(DrawStatus.NOT_DRAWN, list.status)
    assertEquals(now, list.drawnAt)
    assertEquals(NotificationStatus.CANCELLED, registration.status)
    verify { h.lists.save(list) }
  }

  @Test
  fun `should reject malformed or unknown player subjects and invalid participant phones`() {
    val malformed = Harness()
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        assertThrows(ResponseStatusException::class.java) { malformed.service.all("bad-subject") }
            .statusCode,
    )
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        run {
          every { malformed.players.findByUserId(any()) } returns null
          assertThrows(ResponseStatusException::class.java) {
            malformed.service.all(UUID.randomUUID().toString())
          }
        }
            .statusCode,
    )

    val invalidPhone = Harness()
    val player =
        Player(
            id = alice.id,
            name = alice.name,
            phoneNumber = "11999999999",
            user = alice.user,
        )
    every { invalidPhone.players.findByUserId(player.user!!.id) } returns player
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException::class.java) {
              invalidPhone.service.create(
                  player.user!!.id.toString(),
                  CreateSecretSanta("draw", now.plusSeconds(60)),
              )
            }
            .statusCode,
    )
  }

  @Test
  fun `should reject creation when the word pool cannot provide a unique word per player`() {
    val h = Harness()
    h.signedIn()
    every { h.players.findAllById(listOf(bob.id)) } returns listOf(bob)
    every { h.lists.saveAndFlush(any()) } answers { firstArg<SecretSantaList>().apply { id = 9 } }
    every { h.words.findAll() } returns listOf(SecretSantaWord(word = "single"))

    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException::class.java) {
              h.service.create(
                  alice.user!!.id.toString(),
                  CreateSecretSanta("draw", now.plusSeconds(60), listOf(bob.id)),
              )
            }
            .statusCode,
    )
  }

  @Test
  fun `should assign a secret word to a member when their detail is first loaded`() {
    val h = Harness()
    h.signedIn()
    val list = h.openList()
    val member = SecretSantaPlayer(id = 31, ssListId = 9, playerId = alice.id)
    every { h.lists.lockById(9) } returns list
    every { h.entries.findAllBySsListIdOrderByIdAsc(9) } returns listOf(member)
    every { h.players.findAllById(listOf(alice.id)) } returns listOf(alice)
    every { h.entries.save(member) } returns member

    val result = h.service.detail(9, alice.user!!.id.toString())

    assertEquals(1, result.playerCount)
    assertTrue(result.isParticipant)
    assertTrue(result.secretWord!!.isNotBlank())
    verify { h.entries.save(member) }
  }

  @Test
  fun `should reject past draw date updates and missing players`() {
    val h = Harness()
    every { h.lists.lockById(9) } returns h.openList()
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException::class.java) {
              h.service.changeDrawDate(9, ChangeDrawDate(now))
            }
            .statusCode,
    )

    every { h.players.findById(bob.id) } returns Optional.empty()
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException::class.java) { h.service.addPlayer(9, bob.id) }
            .statusCode,
    )
  }

  @Test
  fun `should reject duplicate players and remove requests for absent members`() {
    val h = Harness()
    every { h.lists.lockById(9) } returns h.openList()
    every { h.players.findById(bob.id) } returns Optional.of(bob)
    every { h.entries.findBySsListIdAndPlayerId(9, bob.id) } returns
        SecretSantaPlayer(id = 30, ssListId = 9, playerId = bob.id)
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException::class.java) { h.service.addPlayer(9, bob.id) }
            .statusCode,
    )

    every { h.entries.findBySsListIdAndPlayerId(9, bob.id) } returns null
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException::class.java) { h.service.removePlayer(9, bob.id) }
            .statusCode,
    )
  }

  @Test
  fun `should reject invite registration names and duplicate enrollment`() {
    val h = Harness()
    val list = h.openList()
    every { h.lists.findByInviteToken("invite-token") } returns list
    every { h.lists.lockById(9) } returns list
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException::class.java) {
              h.service.joinByInvite("invite-token", InviteRegistration(" ", "+5511999999999"))
            }
            .statusCode,
    )

    every { h.players.findByPhoneNumber("+5511999999999") } returns alice
    every { h.entries.findBySsListIdAndPlayerId(9, alice.id) } returns
        SecretSantaPlayer(id = 30, ssListId = 9, playerId = alice.id)
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException::class.java) {
              h.service.joinByInvite(
                  "invite-token",
                  InviteRegistration("Alice", "+5511999999999"),
              )
            }
            .statusCode,
    )
  }

  @Test
  fun `should reject a reveal before drawing and reject blank or incorrect words`() {
    val h = Harness()
    every { h.lists.findById(9) } returns Optional.of(h.openList())
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(9, RevealRecipientRequest("word"))
            }
            .statusCode,
    )

    every { h.lists.findById(9) } returns
        Optional.of(h.openList().apply { status = DrawStatus.DRAWN })
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(9, RevealRecipientRequest("  "))
            }
            .statusCode,
    )
    every { h.entries.findBySsListIdAndSecretWord(9, "wrong") } returns null
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(9, RevealRecipientRequest("wrong"))
            }
            .statusCode,
    )
  }

  @Test
  fun `should reject reveal without a recipient or without an existing recipient player`() {
    val h = Harness()
    every { h.lists.findById(9) } returns
        Optional.of(h.openList().apply { status = DrawStatus.DRAWN })
    val noRecipient = SecretSantaPlayer(id = 31, ssListId = 9, playerId = alice.id)
    every { h.entries.findBySsListIdAndSecretWord(9, "word") } returns noRecipient
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(9, RevealRecipientRequest("word"))
            }
            .statusCode,
    )

    noRecipient.recipientId = bob.id
    every { h.players.findById(bob.id) } returns Optional.empty()
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(9, RevealRecipientRequest("word"))
            }
            .statusCode,
    )
  }

  @Test
  fun `should report a missing draw when revealing a recipient`() {
    val h = Harness()
    every { h.lists.findById(99) } returns Optional.empty()

    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException::class.java) {
              h.service.revealRecipient(99, RevealRecipientRequest("word"))
            }
            .statusCode,
    )
  }

  @Test
  fun `should reject leaving when the player is not registered`() {
    val h = Harness()
    h.signedIn()
    every { h.lists.lockById(9) } returns h.openList()
    every { h.entries.findBySsListIdAndPlayerId(9, alice.id) } returns null

    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException::class.java) {
              h.service.leave(9, alice.user!!.id.toString())
            }
            .statusCode,
    )
  }
}
