package com.api.somoscardoso

import java.sql.DriverManager
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

@Testcontainers(disabledWithoutDocker = true)
class UserPlayerMigrationTest {
  companion object {
    @Container @JvmStatic val mysql = MySQLContainer("mysql:8.0").withDatabaseName("migration_test")
  }

  @Test
  fun `V3 preserves legacy credentials phones player IDs and Secret Santa assignments`() {
    val originalHash = BCryptPasswordEncoder().encode("existing password")
    Flyway.configure()
        .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        .target("2")
        .load()
        .migrate()
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
      connection
          .prepareStatement(
              "INSERT INTO players(id,name,email,whatsapp_phone,password_hash,google_subject) VALUES(41,'Legacy Ana','ana@legacy.test','+5511999999999',?,NULL),(42,'Legacy Google','google@legacy.test','+14155552671',NULL,'provider-subject')"
          )
          .use {
            it.setString(1, originalHash)
            it.executeUpdate()
          }
      connection.createStatement().use { sql ->
        sql.executeUpdate(
            "INSERT INTO ss_lists(id,name,draw_at,creator_id,status,drawn_at) VALUES(11,'Legacy draw','2026-12-01 12:00:00',41,'DRAWN','2026-12-01 12:00:01')"
        )
        sql.executeUpdate(
            "INSERT INTO ss_players(id,ss_list_id,player_id,recipient_id) VALUES(21,11,41,42),(22,11,42,41)"
        )
        sql.executeUpdate(
            "INSERT INTO whatsapp_notifications(id,ss_player_id,phone,group_name,recipient_name,status,attempts,next_attempt_at) VALUES(31,21,'+5511999999999','Legacy draw','Legacy Google','SENT',1,'2026-12-01 12:00:02')"
        )
      }
    }
    Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password).load().migrate()
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
      connection.createStatement().use { sql ->
        sql.executeQuery(
                "SELECT p.id,p.name,p.phone_number,p.user_id,u.email,u.password_hash,u.google_subject FROM players p JOIN users u ON p.user_id=u.id ORDER BY p.id"
            )
            .use { rows ->
              assertTrue(rows.next())
              assertEquals(41L, rows.getLong("id"))
              assertEquals("Legacy Ana", rows.getString("name"))
              val first = UUID.fromString(rows.getString("user_id"))
              assertEquals("+5511999999999", rows.getString("phone_number"))
              assertEquals("ana@legacy.test", rows.getString("email"))
              assertEquals(originalHash, rows.getString("password_hash"))
              assertTrue(
                  BCryptPasswordEncoder()
                      .matches("existing password", rows.getString("password_hash"))
              )
              assertTrue(rows.next())
              assertEquals(42L, rows.getLong("id"))
              assertNotEquals(first, UUID.fromString(rows.getString("user_id")))
              assertEquals("+14155552671", rows.getString("phone_number"))
              assertEquals("provider-subject", rows.getString("google_subject"))
              assertNull(rows.getString("password_hash"))
              assertFalse(rows.next())
            }
        sql.executeQuery("SELECT creator_id FROM secret_santa_lists WHERE id=11").use {
          it.next()
          assertEquals(41L, it.getLong(1))
        }
        sql.executeQuery(
                "SELECT player_id,recipient_id FROM secret_santa_players WHERE ss_list_id=11 ORDER BY id"
            )
            .use {
              assertTrue(it.next())
              assertEquals(41L, it.getLong(1))
              assertEquals(42L, it.getLong(2))
              assertTrue(it.next())
              assertEquals(42L, it.getLong(1))
              assertEquals(41L, it.getLong(2))
              assertFalse(it.next())
            }
        sql.executeQuery("SELECT phone,status FROM whatsapp_notifications WHERE id=31").use {
          it.next()
          assertEquals("+5511999999999", it.getString(1))
          assertEquals("SENT", it.getString(2))
        }
        sql.executeQuery(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='migration_test' AND TABLE_NAME='players'"
            )
            .use {
              val columns = mutableSetOf<String>()
              while (it.next()) columns.add(it.getString(1))
              assertFalse(
                  columns.any { column ->
                    column in setOf("email", "password_hash", "google_subject", "whatsapp_phone")
                  }
              )
              assertTrue(columns.containsAll(setOf("id", "user_id", "name", "phone_number")))
            }
        assertThrows(java.sql.SQLException::class.java) {
          sql.executeUpdate(
              "UPDATE players SET user_id='00000000-0000-0000-0000-000000000000' WHERE id=41"
          )
        }
      }
    }
  }
}
