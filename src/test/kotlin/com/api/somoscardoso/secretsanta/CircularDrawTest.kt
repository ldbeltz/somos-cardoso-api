package com.api.somoscardoso.secretsanta

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CircularDrawTest {
  private val draw = CircularDraw()

  @Test
  fun `two players point to each other`() {
    val first = UUID.fromString("10000000-0000-0000-0000-000000000001")
    val second = UUID.fromString("20000000-0000-0000-0000-000000000002")
    assertEquals(mapOf(first to second, second to first), draw.assign(listOf(first, second)))
  }

  @Test
  fun `every shuffled draw is one closed ring and a permutation without self matches`() {
    for (size in 2..100) repeat(10) {
      val players = (1..size).map { UUID.randomUUID() }
      val assignments = draw.assign(players)
      assertEquals(players.toSet(), assignments.keys)
      assertEquals(players.toSet(), assignments.values.toSet())
      assertTrue(assignments.all { (giver, recipient) -> giver != recipient })
      val visited = mutableSetOf<UUID>()
      var current = players.first()
      repeat(size) {
        assertTrue(visited.add(current))
        current = assignments.getValue(current)
      }
      assertEquals(players.first(), current)
      assertEquals(players.toSet(), visited)
    }
  }

  @Test
  fun `duplicate or fewer than two players are rejected`() {
    val id = UUID.randomUUID()
    listOf(emptyList(), listOf(id), listOf(id, id)).forEach { ids ->
      assertThrows(IllegalArgumentException::class.java) { draw.assign(ids) }
    }
  }
}
