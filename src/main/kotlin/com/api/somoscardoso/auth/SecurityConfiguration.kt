package com.api.somoscardoso.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

@Configuration
class SecurityConfiguration {
  @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun filterChain(
      http: HttpSecurity,
      google: GoogleIdentityService,
      @Value("\${GOOGLE_CLIENT_ID:}") clientId: String,
      @Value("\${GOOGLE_CLIENT_SECRET:}") clientSecret: String,
      @Value("\${APP_FRONTEND_URL:http://localhost:3000}") frontendUrl: String,
      @Value("\${APP_GOOGLE_REDIRECT_URI:http://localhost:3000/login/oauth2/code/google}")
      redirectUri: String,
  ): SecurityFilterChain {
    http.authorizeHttpRequests { auth ->
      auth
          .requestMatchers(
              "/api/v1/auth/csrf",
              "/api/v1/auth/providers",
              "/api/v1/auth/register",
              "/api/v1/auth/login",
              "/oauth2/**",
              "/login/oauth2/**",
              "/actuator/health",
              "/error",
          )
          .permitAll()
      auth.anyRequest().authenticated()
    }
    // CSRF remains enabled. The frontend obtains its masked token through /auth/csrf.
    http.securityContext { it.securityContextRepository(HttpSessionSecurityContextRepository()) }
    http.exceptionHandling {
      it.authenticationEntryPoint { _, response, _ ->
        response.status = 401
        response.contentType = "application/json"
        response.writer.write("{\"message\":\"Please sign in\"}")
      }
      it.accessDeniedHandler { _, response, _ ->
        response.status = 403
        response.contentType = "application/json"
        response.writer.write("{\"message\":\"Request forbidden or security token expired\"}")
      }
    }
    http.logout {
      it.logoutUrl("/api/v1/auth/logout")
      it.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
      it.logoutSuccessHandler { _, response, _ -> response.status = 204 }
    }
    if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
      val registration =
          ClientRegistration.withRegistrationId("google")
              .clientId(clientId)
              .clientSecret(clientSecret)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri(redirectUri)
              .scope("openid", "profile", "email")
              .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
              .tokenUri("https://oauth2.googleapis.com/token")
              .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
              .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
              .userNameAttributeName("sub")
              .clientName("Google")
              .build()
      http.oauth2Login {
        it.clientRegistrationRepository(InMemoryClientRegistrationRepository(registration))
        it.userInfoEndpoint { userInfo -> userInfo.oidcUserService(google) }
        it.defaultSuccessUrl(frontendUrl.trimEnd('/') + "/", true)
        it.failureUrl(frontendUrl.trimEnd('/') + "/auth?error=google")
      }
    }
    return http.build()
  }
}
