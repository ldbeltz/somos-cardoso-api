package com.api.somoscardoso.secretsanta

import java.security.SecureRandom
import java.util.Collections
import java.util.LinkedList
import org.springframework.stereotype.Component

/** Shuffled ring: every player gives once and receives once, with no self-match. */
@Component
class CircularDraw {
  private val random = SecureRandom()

  fun assign(playerIds: List<Long>): Map<Long, Long> {
    require(playerIds.size >= 2) { "At least two players are required" }
    require(playerIds.distinct().size == playerIds.size) { "Players must be distinct" }
    val shuffled = playerIds.toMutableList()
    Collections.shuffle(shuffled, random)
    val ring = LinkedList(shuffled)
    return ring.mapIndexed { index, player -> player to ring[(index + 1) % ring.size] }.toMap()
  }
}
