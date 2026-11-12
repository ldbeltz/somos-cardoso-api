package com.api.somoscardoso

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SomoscardosoApplicationTests {
  companion object {
    @Container
    @ServiceConnection // Mapeia automaticamente as propriedades do banco para o Spring
    @JvmStatic
    val mysqlContainer =
        MySQLContainer("mysql:8.0").apply {
          withDatabaseName("testdb")
          withUsername("testuser")
          withPassword("testpass")
        }
  }

  @Test fun contextLoads() {}
}
