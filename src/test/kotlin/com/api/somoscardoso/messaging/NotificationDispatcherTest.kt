package com.api.somoscardoso.messaging

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.domain.PageRequest

class NotificationDispatcherTest {
  private val now = Instant.parse("2026-12-20T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val repository = mock(WhatsAppNotificationRepository::class.java)
  private val transactions = mock(NotificationDeliveryTransactions::class.java)
  private val sender = mock(WhatsAppSender::class.java)

  @Test
  fun `missing provider configuration leaves outbox untouched`() {
    `when`(sender.enabledTypes).thenReturn(emptySet())
    NotificationDispatcher(repository, transactions, sender, clock).dispatch()
    verifyNoInteractions(repository, transactions)
  }

  @Test
  fun `failed provider schedules retry instead of marking sent`() {
    `when`(sender.enabledTypes).thenReturn(setOf(NotificationType.DRAW_RESULT))
    `when`(repository.findDueIds(now, setOf(NotificationType.DRAW_RESULT), PageRequest.of(0, 25)))
        .thenReturn(listOf(1L))
    val notification =
        WhatsAppNotification(
            id = 1,
            phone = "+5511999999999",
            groupName = "Christmas",
            recipientName = "Bob",
            attempts = 1,
        )
    `when`(transactions.claim(1)).thenReturn(notification)
    doThrow(RuntimeException("provider unavailable")).`when`(sender).send(notification)
    NotificationDispatcher(repository, transactions, sender, clock).dispatch()
    verify(transactions).finish(1, 1, false)
  }

  @Test
  fun `accepted provider request marks notification sent`() {
    `when`(sender.enabledTypes).thenReturn(setOf(NotificationType.DRAW_RESULT))
    `when`(repository.findDueIds(now, setOf(NotificationType.DRAW_RESULT), PageRequest.of(0, 25)))
        .thenReturn(listOf(1L))
    val notification =
        WhatsAppNotification(
            id = 1,
            phone = "+5511999999999",
            groupName = "Christmas",
            recipientName = "Bob",
            attempts = 1,
        )
    `when`(transactions.claim(1)).thenReturn(notification)
    NotificationDispatcher(repository, transactions, sender, clock).dispatch()
    verify(transactions).finish(1, 1, true)
  }

  @Test
  fun `backoff grows and remains bounded`() {
    assertEquals(30L, NotificationDeliveryTransactions.retryDelay(1))
    assertEquals(60L, NotificationDeliveryTransactions.retryDelay(2))
    assertEquals(21600L, NotificationDeliveryTransactions.retryDelay(1000))
  }

  @Test
  fun `claim will not resend accepted or currently leased notifications`() {
    val real = NotificationDeliveryTransactions(repository, clock)
    `when`(repository.lockById(1))
        .thenReturn(WhatsAppNotification(id = 1, status = NotificationStatus.SENT))
    assertNull(real.claim(1))
    `when`(repository.lockById(2))
        .thenReturn(
            WhatsAppNotification(
                id = 2,
                status = NotificationStatus.SENDING,
                nextAttemptAt = now.plusSeconds(60),
            )
        )
    assertNull(real.claim(2))
  }

  @Test
  fun `claim rejects cancelled and expired registration notices`() {
    val real = NotificationDeliveryTransactions(repository, clock)
    val cancelled = WhatsAppNotification(id = 3, status = NotificationStatus.CANCELLED)
    `when`(repository.lockById(3)).thenReturn(cancelled)
    assertNull(real.claim(3))
    verify(repository, never()).save(cancelled)

    val expired =
        WhatsAppNotification(
            id = 4,
            type = NotificationType.REGISTRATION,
            scheduledDrawAt = now,
            status = NotificationStatus.PENDING,
        )
    `when`(repository.lockById(4)).thenReturn(expired)
    assertNull(real.claim(4))
    assertEquals(NotificationStatus.CANCELLED, expired.status)
    verify(repository).save(expired)
  }

  @Test
  fun `expired claim can resume after a crashed worker and failed finish sets backoff`() {
    val real = NotificationDeliveryTransactions(repository, clock)
    val row =
        WhatsAppNotification(
            id = 1,
            status = NotificationStatus.SENDING,
            attempts = 1,
            nextAttemptAt = now.minusSeconds(1),
        )
    `when`(repository.lockById(1)).thenReturn(row)
    assertNotNull(real.claim(1))
    assertEquals(2, row.attempts)
    assertEquals(now.plusSeconds(300), row.nextAttemptAt)
    real.finish(1, 2, false)
    assertEquals(NotificationStatus.PENDING, row.status)
    assertEquals(now.plusSeconds(60), row.nextAttemptAt)
  }
}
