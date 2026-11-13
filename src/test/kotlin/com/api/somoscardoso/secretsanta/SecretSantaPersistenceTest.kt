package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.NotificationStatus
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
    val list = lists.findById(detail.id).orElseThrow()
    list.drawAt = Instant.now().minusSeconds(60)
    lists.saveAndFlush(list)
    val beforeNotifications = notifications.count()
    assertTrue(service.drawIfDue(detail.id))
    entries.flush()
    notifications.flush()
    assertFalse(service.drawIfDue(detail.id))
    assertEquals(beforeNotifications + 3, notifications.count())
    val members = entries.findAllBySsListIdOrderByIdAsc(detail.id)
    assertEquals(members.map { it.playerId }.toSet(), members.map { it.recipientId }.toSet())
    assertTrue(members.all { it.playerId != it.recipientId })
    assertTrue(
        members.all {
          notifications.findBySsPlayerId(it.id!!)!!.status == NotificationStatus.PENDING
        }
    )
    assertNotNull(service.detail(detail.id, alice.user!!.id.toString()).myRecipient)
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
