package com.api.somoscardoso.auth

import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.UserRepository
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "GOOGLE_CLIENT_ID=",
            "GOOGLE_CLIENT_SECRET=",
        ]
)
@AutoConfigureMockMvc
class AuthIntegrationTests {
  @Autowired lateinit var mvc: MockMvc
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  lateinit var players: PlayerRepository
  @Autowired lateinit var googleAccounts: GoogleAccountService
  @Autowired lateinit var users: UserRepository

  @Test
  fun `failed Google player provisioning rolls back its authentication account`() {
    val token =
        org.springframework.security.oauth2.core.oidc.OidcIdToken(
            "token",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(300),
            mapOf(
                "sub" to "rollback-subject",
                "email" to "rollback@example.com",
                "email_verified" to true,
                "name" to "Rollback",
            ),
        )
    val remote =
        org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
            listOf(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLAYER")
            ),
            token,
        )
    org.mockito.Mockito.doThrow(
            org.springframework.dao.DataIntegrityViolationException("simulated player failure")
        )
        .`when`(players)
        .saveAndFlush(org.mockito.Mockito.any(com.api.somoscardoso.player.Player::class.java))
    assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
      googleAccounts.resolve(remote)
    }
    assertNull(users.findByEmail("rollback@example.com"))
  }

  @Test
  fun `registration persists a hashed password and authenticated session then logout revokes access`() {
    val oldSession = MockHttpSession()
    val oldId = oldSession.id
    val result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .session(oldSession)
                    .with {
                      it.setRequestedSessionId(oldId)
                      it.setRequestedSessionIdValid(true)
                      it
                    }
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"name":"Ana","email":"ana@example.com","password":"correct horse battery","phoneNumber":"(11) 99999-9999"}"""
                    )
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Ana"))
            .andExpect(jsonPath("$.userId").isString)
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn()
    val session = result.request.session as MockHttpSession
    assertNotEquals(oldId, session.id)
    val stored = users.findByEmail("ana@example.com")!!
    assertNotEquals("correct horse battery", stored.passwordHash)
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.email").value("ana@example.com"))
    mvc.perform(
            put("/api/v1/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"21 99999-9999"}""")
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.phoneNumber").value("+5521999999999"))
    mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent)
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized)
    val signedIn =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"ANA@example.com","password":"correct horse battery"}""")
            )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession
    mvc.perform(get("/api/v1/auth/me").session(signedIn)).andExpect(status().isOk)
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ana@example.com","password":"wrong"}""")
        )
        .andExpect(status().isUnauthorized)
        .andExpect(jsonPath("$.message").value("Invalid email or password"))
  }

  @Test
  fun `successful authentication invalidates the previous csrf token`() {
    val tokenResponse = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk).andReturn()
    val session = tokenResponse.request.session as MockHttpSession
    val oldToken = JsonPath.read<String>(tokenResponse.response.contentAsString, "$.token")
    mvc.perform(
            post("/api/v1/auth/register")
                .session(session)
                .header("X-CSRF-TOKEN", oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"CSRF Player","email":"csrf@example.com","password":"long enough password","phoneNumber":"+5511777777777"}"""
                )
        )
        .andExpect(status().isOk)
    mvc.perform(
            put("/api/v1/auth/me")
                .session(session)
                .header("X-CSRF-TOKEN", oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"+5511666666666"}""")
        )
        .andExpect(status().isForbidden)
    val fresh =
        mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk).andReturn()
    val freshToken = JsonPath.read<String>(fresh.response.contentAsString, "$.token")
    mvc.perform(
            put("/api/v1/auth/me")
                .session(session)
                .header("X-CSRF-TOKEN", freshToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"+5511666666666"}""")
        )
        .andExpect(status().isOk)
  }

  @Test
  fun `csrf required on login and anonymous users cannot list players`() {
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.token").isString)
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"x@example.com","password":"anything"}""")
        )
        .andExpect(status().isForbidden)
    mvc.perform(get("/api/v1/players")).andExpect(status().isUnauthorized)
    mvc.perform(get("/api/v1/auth/providers"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.google").value(false))
  }

  @Test
  fun `registration validates phone password and duplicate email`() {
    val body =
        """{"name":"Jo","email":"jo@example.com","password":"long enough password","phoneNumber":"+5511888888888"}"""
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isOk)
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isConflict)
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("+5511888888888", "11999"))
        )
        .andExpect(status().isBadRequest)
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("long enough password", "short"))
        )
        .andExpect(status().isBadRequest)
  }
}
