package com.api.somoscardoso.secretsanta

import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration
@EnableScheduling
class DrawSchedulingConfiguration {
  @Bean fun applicationClock(): Clock = Clock.systemUTC()
}

@Component
class DrawScheduler(
    private val lists: SSListRepository,
    private val service: SecretSantaService,
    private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${app.draw.poll-ms:15000}")
  fun runDueDraws() {
    lists.findAllByStatusAndDrawAtLessThanEqual(DrawStatus.SCHEDULED, clock.instant()).forEach {
        list ->
      try {
        service.drawIfDue(list.id!!)
      } catch (_: Exception) {
        logger.warn("Scheduled draw {} could not complete; it will be retried", list.id)
      }
    }
  }
}
