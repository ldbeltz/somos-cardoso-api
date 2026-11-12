package com.api.somoscardoso.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:google;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "GOOGLE_CLIENT_ID=test-google-client",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "APP_GOOGLE_REDIRECT_URI=http://localhost:3000/login/oauth2/code/google",
        ]
)
@AutoConfigureMockMvc
class GoogleLoginConfigurationTests {
  @Autowired lateinit var mvc: MockMvc

  @Test
  fun `configured google login redirects with state and browser origin callback`() {
    mvc.perform(get("/api/v1/auth/providers"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.google").value(true))
    val result =
        mvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection)
            .andReturn()
    val target = result.response.redirectedUrl!!
    assertTrue(target.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
    assertTrue(target.contains("state="))
    assertTrue(
        java.net.URLDecoder.decode(target, java.nio.charset.StandardCharsets.UTF_8)
            .contains("redirect_uri=http://localhost:3000/login/oauth2/code/google")
    )
    assertNotNull(result.request.session)
  }
}
