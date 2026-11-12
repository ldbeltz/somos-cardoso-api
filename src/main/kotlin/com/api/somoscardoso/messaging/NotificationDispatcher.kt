package com.api.somoscardoso.messaging

import java.time.Clock
import java.time.Duration
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A claim leases a row; a crashed process can resume it after five minutes. */
@Service
class NotificationDeliveryTransactions(
    private val repository: WhatsAppNotificationRepository,
    private val clock: Clock,
) {
  @Transactional
  fun claim(id: Long): WhatsAppNotification? {
    val row = repository.lockById(id) ?: return null
    if (row.status == NotificationStatus.SENT || row.nextAttemptAt.isAfter(clock.instant()))
        return null
    row.status = NotificationStatus.SENDING
    row.attempts++
    row.nextAttemptAt = clock.instant().plus(Duration.ofMinutes(5))
    repository.saveAndFlush(row)
    return row
  }

  @Transactional
  fun finish(id: Long, attempt: Int, success: Boolean) {
    val row = repository.lockById(id) ?: return
    if (row.status != NotificationStatus.SENDING || row.attempts != attempt) return
    row.status = if (success) NotificationStatus.SENT else NotificationStatus.PENDING
    if (!success) row.nextAttemptAt = clock.instant().plusSeconds(retryDelay(row.attempts))
    repository.save(row)
  }

  companion object {
    fun retryDelay(attempt: Int): Long =
        (30L shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(21600L)
  }
}

@Service
class NotificationDispatcher(
    private val repository: WhatsAppNotificationRepository,
    private val transactions: NotificationDeliveryTransactions,
    private val sender: WhatsAppSender,
    private val clock: Clock,
) {
  @Scheduled(fixedDelayString = "\${app.whatsapp.poll-ms:15000}")
  fun dispatch() {
    // Missing credentials must never consume attempts or mark a message as sent.
    if (!sender.enabled) return
    repository.findDueIds(clock.instant(), PageRequest.of(0, 25)).forEach { id ->
      val message = transactions.claim(id) ?: return@forEach
      val success =
          try {
            sender.send(message.phone, message.groupName, message.recipientName)
            true
          } catch (_: Exception) {
            false
          }
      transactions.finish(id, message.attempts, success)
    }
  }
}
