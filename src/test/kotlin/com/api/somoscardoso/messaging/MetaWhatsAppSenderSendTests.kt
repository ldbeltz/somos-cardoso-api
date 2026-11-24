package com.api.somoscardoso.messaging

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class MetaWhatsAppSenderSendTests {
  private fun sender() =
      MetaWhatsAppSender(
          true,
          true,
          "token",
          "phone-id",
          "v25.0",
          "result",
          "pt_BR",
          "registration",
          "pt_BR",
          "America/Maceio",
      )

  private fun notification() =
      WhatsAppNotification(
          id = 7,
          phone = "+5511999999999",
          groupName = "Natal",
          type = NotificationType.REGISTRATION,
          scheduledDrawAt = Instant.parse("2026-12-20T12:00:00Z"),
      )

  private fun client(): Pair<RestClient, MockRestServiceServer> {
    val builder = RestClient.builder().baseUrl("https://graph.facebook.com")
    val server = MockRestServiceServer.bindTo(builder).build()
    return builder.build() to server
  }

  @Test
  fun `should send enabled template when the API accepts the request`() {
    val (client, server) = client()
    server
        .expect(requestTo("https://graph.facebook.com/v25.0/phone-id/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON))

    sender().send(notification(), client)

    server.verify()
  }

  @Test
  fun `should rethrow an API rejection`() {
    val (client, server) = client()
    server
        .expect(requestTo("https://graph.facebook.com/v25.0/phone-id/messages"))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest()
        )

    assertThrows(org.springframework.web.client.RestClientResponseException::class.java) {
      sender().send(notification(), client)
    }
    server.verify()
  }

  @Test
  fun `should rethrow a failure before receiving an API response`() {
    val (client, server) = client()
    server
        .expect(requestTo("https://graph.facebook.com/v25.0/phone-id/messages"))
        .andRespond(withException(java.io.IOException("connection failed")))

    assertThrows(org.springframework.web.client.ResourceAccessException::class.java) {
      sender().send(notification(), client)
    }
    server.verify()
  }

  @Test
  fun `should reject sending with a disabled template`() {
    val disabled = sender().copyForTesting()
    assertThrows(IllegalStateException::class.java) { disabled.send(notification()) }
  }
}

private fun MetaWhatsAppSender.copyForTesting() =
    MetaWhatsAppSender(
        false,
        false,
        "token",
        "phone-id",
        "v25.0",
        "result",
        "pt_BR",
        "registration",
        "pt_BR",
        "America/Maceio",
    )
