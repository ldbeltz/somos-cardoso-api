package com.api.somoscardoso.messaging

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

interface WhatsAppSender {
  val enabled: Boolean
  val enabledTypes: Set<NotificationType>

  fun send(notification: WhatsAppNotification)
}

/** Sends draw-result and enrollment templates with independent readiness settings. */
@Component
class MetaWhatsAppSender(
    @Value("\${app.whatsapp.enabled:false}") override val enabled: Boolean,
    @Value("\${app.whatsapp.registration.enabled:false}") registrationConfiguredEnabled: Boolean,
    @Value("\${app.whatsapp.access-token:}") private val accessToken: String,
    @Value("\${app.whatsapp.phone-number-id:}") private val phoneNumberId: String,
    @Value("\${app.whatsapp.api-version:v23.0}") private val apiVersion: String,
    @Value("\${app.whatsapp.template-name:}") private val resultTemplateName: String,
    @Value("\${app.whatsapp.template-language:en_US}") private val resultTemplateLanguage: String,
    @Value("\${app.whatsapp.registration.template-name:}")
    private val registrationTemplateName: String,
    @Value("\${app.whatsapp.registration.template-language:pt_PT}")
    private val registrationTemplateLanguage: String,
    @Value("\${app.whatsapp.registration.time-zone:America/Maceio}")
    private val registrationTimeZone: String,
) : WhatsAppSender {
  private val credentialsReady = accessToken.isNotBlank() && phoneNumberId.isNotBlank()
  private val resultReady = enabled && credentialsReady && resultTemplateName.isNotBlank()
  private val registrationReady =
      enabled &&
          registrationConfiguredEnabled &&
          credentialsReady &&
          registrationTemplateName.isNotBlank()

  override val enabledTypes: Set<NotificationType> = buildSet {
    if (resultReady) add(NotificationType.DRAW_RESULT)
    if (registrationReady) add(NotificationType.REGISTRATION)
  }

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

  override fun send(notification: WhatsAppNotification) {
    val payload = payload(notification)
    check(notification.type in enabledTypes) {
      "WhatsApp template is not configured for ${notification.type}"
    }
    client
        .post()
        .uri("/$apiVersion/$phoneNumberId/messages")
        .headers { it.setBearerAuth(accessToken) }
        .body(payload)
        .retrieve()
        .toBodilessEntity()
  }

  /** Package-visible payload construction keeps template parameters directly testable. */
  internal fun payload(notification: WhatsAppNotification): Map<String, Any> {
    val (name, language, parameters) =
        when (notification.type) {
          NotificationType.DRAW_RESULT ->
              Triple(
                  resultTemplateName,
                  resultTemplateLanguage,
                  listOf(notification.groupName, notification.recipientName),
              )
          NotificationType.REGISTRATION -> {
            val scheduledAt =
                requireNotNull(notification.scheduledDrawAt) {
                  "Registration notice must include scheduled draw time"
                }
            Triple(
                registrationTemplateName,
                registrationTemplateLanguage,
                listOf(notification.groupName, formatDrawAt(scheduledAt)),
            )
          }
        }
    return mapOf(
        "messaging_product" to "whatsapp",
        "to" to notification.phone.removePrefix("+"),
        "type" to "template",
        "template" to
            mapOf(
                "name" to name,
                "language" to mapOf("code" to language),
                "components" to
                    listOf(
                        mapOf(
                            "type" to "body",
                            "parameters" to
                                parameters.map { mapOf("type" to "text", "text" to it) },
                        )
                    ),
            ),
    )
  }

  private fun formatDrawAt(instant: Instant): String =
      DRAW_AT_FORMAT.format(instant.atZone(ZoneId.of(registrationTimeZone)))

  companion object {
    private val DRAW_AT_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-PT"))
  }
}
