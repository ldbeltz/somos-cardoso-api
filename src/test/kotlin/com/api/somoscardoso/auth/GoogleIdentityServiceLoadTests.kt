package com.api.somoscardoso.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser

class GoogleIdentityServiceLoadTests {
  @Test
  fun `should resolve the user returned by the OIDC service`() {
    val accounts = mockk<GoogleAccountService>()
    val service = GoogleIdentityService(accounts)
    val now = Instant.now()
    val token =
        OidcIdToken(
            "id-token",
            now,
            now.plusSeconds(300),
            mapOf(
                "iss" to "https://accounts.google.com",
                "sub" to "subject",
                "aud" to listOf("client"),
            ),
        )
    val registration =
        ClientRegistration.withRegistrationId("google")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.google.com/o/oauth2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("")
            .userNameAttributeName("sub")
            .clientName("Google")
            .build()
    val request =
        org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest(
            registration,
            OAuth2AccessToken(TokenType.BEARER, "access-token", now, now.plusSeconds(300)),
            token,
        )
    val expected = DefaultOidcUser(listOf(SimpleGrantedAuthority("ROLE_USER")), token)
    every { accounts.resolve(any()) } returns expected

    val actual = service.loadUser(request)

    assertSame(expected, actual)
    verify { accounts.resolve(any()) }
  }
}
