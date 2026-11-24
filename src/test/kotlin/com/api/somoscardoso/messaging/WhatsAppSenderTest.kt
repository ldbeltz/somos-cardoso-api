package com.api.somoscardoso.messaging

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppSenderTest {
  private fun sender(
      masterEnabled: Boolean = true,
      registrationEnabled: Boolean = true,
      resultTemplate: String = "amigo_secreto_resultado",
      registrationTemplate: String = "amigo_secreto_inscricao",
  ) =
      MetaWhatsAppSender(
          masterEnabled,
          registrationEnabled,
          "token",
          "1499882366535367",
          "v23.0",
          resultTemplate,
          "pt_BR",
          registrationTemplate,
          "pt_PT",
          "America/Maceio",
      )

  @Test
  fun `registration template gets draw name and localized scheduled date only`() {
    val sender = sender()
    val registration =
        WhatsAppNotification(
            phone = "+5511999999999",
            groupName = "Natal Cardoso",
            type = NotificationType.REGISTRATION,
            scheduledDrawAt = Instant.parse("2026-12-20T12:00:00Z"),
        )
    val payload = sender.payload(registration)
    val template = payload["template"] as Map<*, *>
    assertEquals("amigo_secreto_inscricao", template["name"])
    assertEquals(mapOf("code" to "pt_PT"), template["language"])
    val components = template["components"] as List<*>
    val parameters = ((components.single() as Map<*, *>)["parameters"] as List<*>)
    assertEquals(
        listOf("Natal Cardoso", "20/12/2026 09:00"),
        parameters.map { (it as Map<*, *>)["text"] },
    )
    assertEquals("5511999999999", payload["to"])
  }

  @Test
  fun `registration readiness is separate from result template configuration`() {
    val registrationOnly = sender(resultTemplate = "")
    assertTrue(NotificationType.REGISTRATION in registrationOnly.enabledTypes)
    assertFalse(NotificationType.DRAW_RESULT in registrationOnly.enabledTypes)

    val resultOnly = sender(registrationEnabled = false)
    assertTrue(NotificationType.DRAW_RESULT in resultOnly.enabledTypes)
    assertFalse(NotificationType.REGISTRATION in resultOnly.enabledTypes)

    assertTrue(sender(masterEnabled = false).enabledTypes.isEmpty())
  }

  @Test
  fun `result parameter behavior remains unchanged`() {
    val result =
        sender()
            .payload(
                WhatsAppNotification(
                    phone = "+5511999999999",
                    groupName = "Natal",
                    recipientName = "Bia",
                )
            )
    val template = result["template"] as Map<*, *>
    assertEquals("amigo_secreto_resultado", template["name"])
    assertEquals(mapOf("code" to "pt_BR"), template["language"])
    val components = template["components"] as List<*>
    val parameters = ((components.single() as Map<*, *>)["parameters"] as List<*>)
    assertEquals(listOf("Natal", "Bia"), parameters.map { (it as Map<*, *>)["text"] })
  }

  @Test
  fun `should reject a registration payload without its scheduled draw time`() {
    val notification =
        WhatsAppNotification(
            phone = "+5511999999999",
            groupName = "Natal",
            type = NotificationType.REGISTRATION,
        )

    assertThrows(IllegalArgumentException::class.java) { sender().payload(notification) }
  }
}
