package com.api.somoscardoso.secretsanta

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CircularDrawTest {
  private val draw = CircularDraw()

  @Test
  fun `two players point to each other`() {
    assertEquals(mapOf(1L to 2L, 2L to 1L), draw.assign(listOf(1, 2)))
  }

  @Test
  fun `every shuffled draw is one closed ring and a permutation without self matches`() {
    for (size in 2..100) repeat(10) {
      val players = (1L..size.toLong()).toList()
      val assignments = draw.assign(players)
      assertEquals(players.toSet(), assignments.keys)
      assertEquals(players.toSet(), assignments.values.toSet())
      assertTrue(assignments.all { (giver, recipient) -> giver != recipient })
      val visited = mutableSetOf<Long>()
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
    listOf(emptyList(), listOf(1L), listOf(1L, 1L)).forEach { ids ->
      assertThrows(IllegalArgumentException::class.java) { draw.assign(ids) }
    }
  }
}
