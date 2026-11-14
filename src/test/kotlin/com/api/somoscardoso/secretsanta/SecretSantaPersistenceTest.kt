package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.NotificationStatus
import com.api.somoscardoso.messaging.NotificationType
import com.api.somoscardoso.messaging.WhatsAppNotificationRepository
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** Real Spring/JPA/transaction smoke test; MySQL-specific coverage remains in Testcontainers. */
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "GOOGLE_CLIENT_ID=",
            "GOOGLE_CLIENT_SECRET=",
        ]
)
@AutoConfigureMockMvc
@Transactional
class SecretSantaPersistenceTest {
  private val phoneSequence = AtomicLong()
  @Autowired lateinit var service: SecretSantaService
  @Autowired lateinit var players: PlayerRepository
  @Autowired lateinit var lists: SecretSantaListRepository
  @Autowired lateinit var entries: SecretSantaPlayerRepository
  @Autowired lateinit var notifications: WhatsAppNotificationRepository
  @Autowired lateinit var mvc: MockMvc

  private fun player(name: String) =
      players.saveAndFlush(
          Player(
              name = name,
              user = User(email = "$name@draw.test"),
              phoneNumber =
                  "+551199999${phoneSequence.incrementAndGet().toString().padStart(4, '0')}",
          )
      )

  @Test
  fun `creation draw and repeat execution persist only one closed ring and one outbox row per player`() {
    val alice = player("ringAlice")
    val bob = player("ringBob")
    val clara = player("ringClara")
    val outsider = player("ringOutsider")
    val joiner = player("ringJoiner")
    val detail =
        service.create(
            alice.user!!.id.toString(),
            CreateSecretSanta(
                "Cardoso Christmas",
                Instant.now().plusSeconds(3600),
                listOf(bob.id!!, clara.id!!),
            ),
        )
    assertEquals(3, detail.playerCount)
    assertNull(detail.myRecipient)
    var members = entries.findAllBySsListIdOrderByIdAsc(detail.id)
    assertEquals(
        3,
        members.count {
          notifications.findBySsPlayerIdAndType(it.id!!, NotificationType.REGISTRATION) != null
        },
    )
    assertTrue(
        members.all {
          notifications
              .findBySsPlayerIdAndType(it.id!!, NotificationType.REGISTRATION)!!
              .scheduledDrawAt == detail.drawAt
        }
    )
    val dueAtCreation =
        notifications.findDueIds(
            Instant.now(),
            setOf(NotificationType.REGISTRATION),
            PageRequest.of(0, 25),
        )
    assertEquals(3, dueAtCreation.size)
    assertTrue(
        notifications
            .findDueIds(
                Instant.now(),
                setOf(NotificationType.DRAW_RESULT),
                PageRequest.of(0, 25),
            )
            .isEmpty()
    )
    service.join(detail.id, joiner.user!!.id.toString())
    assertEquals(4L, notifications.count())
    assertEquals(4, entries.findAllBySsListIdOrderByIdAsc(detail.id).size)
    service.join(detail.id, joiner.user!!.id.toString())
    assertEquals(4L, notifications.count())
    val list = lists.findById(detail.id).orElseThrow()
    list.drawAt = Instant.now().minusSeconds(60)
    lists.saveAndFlush(list)
    val beforeNotifications = notifications.count()
    assertTrue(service.drawIfDue(detail.id))
    entries.flush()
    notifications.flush()
    assertFalse(service.drawIfDue(detail.id))
    assertEquals(beforeNotifications + 4, notifications.count())
    members = entries.findAllBySsListIdOrderByIdAsc(detail.id)
    assertEquals(members.map { it.playerId }.toSet(), members.map { it.recipientId }.toSet())
    assertTrue(members.all { it.playerId != it.recipientId })
    assertTrue(
        members.all {
          notifications.findBySsPlayerIdAndType(it.id!!, NotificationType.DRAW_RESULT)!!.status ==
              NotificationStatus.PENDING
        }
    )
    assertTrue(
        members.all {
          notifications.findBySsPlayerIdAndType(it.id!!, NotificationType.REGISTRATION)!!.status ==
              NotificationStatus.CANCELLED
        }
    )
    val afterDraw = service.detail(detail.id, alice.user!!.id.toString())
    assertNotNull(afterDraw.myRecipient)
    assertEquals(NotificationStatus.PENDING.name, afterDraw.notificationStatus)
    assertEquals(
        403,
        assertThrows(ResponseStatusException::class.java) {
              service.detail(detail.id, outsider.user!!.id.toString())
            }
            .statusCode
            .value(),
    )
    assertFalse(
        service.all(outsider.user!!.id.toString()).first { it.id == detail.id }.isParticipant
    )
  }

  @Test
  fun `guest invite enrollment queues a notice for the new participant`() {
    val alice = player("inviteAlice")
    val bob = player("inviteBob")
    val detail =
        service.create(
            alice.user!!.id.toString(),
            CreateSecretSanta("Invite draw", Instant.now().plusSeconds(3600), listOf(bob.id!!)),
        )
    service.joinByInvite(detail.inviteToken, InviteRegistration("Guest", "+5511977777777"))
    val guest = players.findByPhoneNumber("+5511977777777")!!
    val membership = entries.findBySsListIdAndPlayerId(detail.id, guest.id!!)!!
    val notice =
        notifications.findBySsPlayerIdAndType(membership.id!!, NotificationType.REGISTRATION)!!
    assertEquals("Invite draw", notice.groupName)
    assertEquals(guest.phoneNumber, notice.phone)
    assertEquals(detail.drawAt, notice.scheduledDrawAt)
    assertEquals("", notice.recipientName)
    assertEquals(3L, notifications.count())
  }

  @Test
  fun `closed invite join returns a clear conflict message`() {
    val alice = player("closedInviteAlice")
    val bob = player("closedInviteBob")
    val detail =
        service.create(
            alice.user!!.id.toString(),
            CreateSecretSanta(
                "Closed invite draw",
                Instant.now().plusSeconds(3600),
                listOf(bob.id!!),
            ),
        )
    val list = lists.findById(detail.id).orElseThrow()
    list.status = DrawStatus.DRAWN
    lists.saveAndFlush(list)

    mvc.perform(
            post("/api/v1/secret-santas/invites/${detail.inviteToken}/join")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Guest","phoneNumber":"+5511977777777"}"""),
        )
        .andExpect(status().isConflict)
        .andExpect(
            jsonPath("$.message")
                .value(
                    "This invite is closed. Players can only join a scheduled draw before its draw time."
                ),
        )
  }

  @Test
  fun `leaving removes that membership outbox rows before deleting it`() {
    val alice = player("leaveAlice")
    val bob = player("leaveBob")
    val clara = player("leaveClara")
    val detail =
        service.create(
            alice.user!!.id.toString(),
            CreateSecretSanta(
                "Leave draw",
                Instant.now().plusSeconds(3600),
                listOf(bob.id!!, clara.id!!),
            ),
        )
    val bobMembership = entries.findBySsListIdAndPlayerId(detail.id, bob.id!!)!!
    assertEquals(3L, notifications.count())
    service.leave(detail.id, bob.user!!.id.toString())
    assertNull(entries.findBySsListIdAndPlayerId(detail.id, bob.id!!))
    assertEquals(2L, notifications.count())
    assertNull(
        notifications.findBySsPlayerIdAndType(bobMembership.id!!, NotificationType.REGISTRATION)
    )
  }

  @Test
  fun `http access requires login csrf and membership without exposing other assignments`() {
    val alice = player("httpAlice")
    val bob = player("httpBob")
    val outsider = player("httpOutsider")
    val detail =
        service.create(
            alice.user!!.id.toString(),
            CreateSecretSanta("HTTP draw", Instant.now().plusSeconds(3600), listOf(bob.id!!)),
        )
    mvc.perform(get("/api/v1/secret-santas")).andExpect(status().isUnauthorized)
    mvc.perform(get("/api/v1/secret-santas/${detail.id}").with(user(outsider.user!!.id.toString())))
        .andExpect(status().isForbidden)
    mvc.perform(get("/api/v1/secret-santas/${detail.id}").with(user(alice.user!!.id.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.players[0].recipientId").doesNotExist())
        .andExpect(jsonPath("$.myRecipient").isEmpty)
    mvc.perform(
            post("/api/v1/secret-santas/${detail.id}/join")
                .with(user(outsider.user!!.id.toString()))
        )
        .andExpect(status().isForbidden)
    mvc.perform(
            post("/api/v1/secret-santas/${detail.id}/join")
                .with(user(outsider.user!!.id.toString()))
                .with(csrf())
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.playerCount").value(3))
    mvc.perform(
            delete("/api/v1/secret-santas/${detail.id}/players/me")
                .with(user(outsider.user!!.id.toString()))
                .with(csrf())
        )
        .andExpect(status().isNoContent)
    mvc.perform(
            post("/api/v1/secret-santas")
                .with(user(alice.user!!.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Too small","drawAt":"2099-12-24T12:00:00Z","playerIds":[]}""")
        )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.playerCount").value(1))
  }
}
