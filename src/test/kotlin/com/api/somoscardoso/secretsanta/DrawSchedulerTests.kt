package com.api.somoscardoso.secretsanta

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrawSchedulerTests {
  @Test
  fun `should run every due draw and keep processing after a draw failure`() {
    val now = Instant.parse("2026-12-20T12:00:00Z")
    val lists = mockk<SecretSantaListRepository>()
    val service = mockk<SecretSantaService>(relaxed = true)
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    every { lists.findAllByStatusAndDrawAtLessThanEqual(DrawStatus.SCHEDULED, now) } returns
        listOf(
            SecretSantaList(id = 1),
            SecretSantaList(id = 2),
        )
    every { service.drawIfDue(1) } returns true
    every { service.drawIfDue(2) } throws IllegalStateException("retry later")

    DrawScheduler(lists, service, clock).runDueDraws()

    verify { service.drawIfDue(1) }
    verify { service.drawIfDue(2) }
    assertEquals(Clock.systemUTC().zone, DrawSchedulingConfiguration().applicationClock().zone)
  }
}
