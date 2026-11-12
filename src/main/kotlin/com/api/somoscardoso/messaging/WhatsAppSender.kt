package com.api.somoscardoso.messaging

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

interface WhatsAppSender {
  val enabled: Boolean

  fun send(phone: String, groupName: String, recipientName: String)
}

/** Sends a pre-approved body template with group name and recipient name parameters. */
@Component
class MetaWhatsAppSender(
    @Value("\${app.whatsapp.enabled:false}") configuredEnabled: Boolean,
    @Value("\${app.whatsapp.access-token:}") private val accessToken: String,
    @Value("\${app.whatsapp.phone-number-id:}") private val phoneNumberId: String,
    @Value("\${app.whatsapp.api-version:v23.0}") private val apiVersion: String,
    @Value("\${app.whatsapp.template-name:}") private val templateName: String,
    @Value("\${app.whatsapp.template-language:en_US}") private val templateLanguage: String,
) : WhatsAppSender {
  override val enabled =
      configuredEnabled && listOf(accessToken, phoneNumberId, templateName).all { it.isNotBlank() }
  private val client: RestClient =
      RestClient.builder()
          .requestFactory(
              JdkClientHttpRequestFactory(
                      java.net.http.HttpClient.newBuilder()
                          .connectTimeout(Duration.ofSeconds(5))
                          .build()
                  )
                  .apply { setReadTimeout(Duration.ofSeconds(15)) }
          )
          .baseUrl("https://graph.facebook.com")
          .build()

  override fun send(phone: String, groupName: String, recipientName: String) {
    check(enabled) { "WhatsApp is not configured" }
    client
        .post()
        .uri("/$apiVersion/$phoneNumberId/messages")
        .headers { it.setBearerAuth(accessToken) }
        .body(
            mapOf(
                "messaging_product" to "whatsapp",
                "to" to phone.removePrefix("+"),
                "type" to "template",
                "template" to
                    mapOf(
                        "name" to templateName,
                        "language" to mapOf("code" to templateLanguage),
                        "components" to
                            listOf(
                                mapOf(
                                    "type" to "body",
                                    "parameters" to
                                        listOf(
                                            mapOf("type" to "text", "text" to groupName),
                                            mapOf("type" to "text", "text" to recipientName),
                                        ),
                                )
                            ),
                    ),
            )
        )
        .retrieve()
        .toBodilessEntity()
  }
}
