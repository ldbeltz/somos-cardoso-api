package com.api.somoscardoso.auth

import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import com.api.somoscardoso.user.User
import com.api.somoscardoso.user.UserRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleIdentityService(private val accounts: GoogleAccountService) : OidcUserService() {
  override fun loadUser(request: OidcUserRequest): OidcUser =
      accounts.resolve(super.loadUser(request))
}

/** A separate proxied bean makes User and Player provisioning one transaction. */
@Service
class GoogleAccountService(
    private val players: PlayerRepository,
    private val users: UserRepository,
) {
  @Transactional
  fun resolve(remote: OidcUser): OidcUser {
    if (remote.emailVerified != true || remote.email.isNullOrBlank())
        fail("A verified Google email is required")
    val subject = remote.subject ?: fail("A Google subject is required")
    val email = requireNotNull(remote.email).trim().lowercase()
    var user = users.findByGoogleSubject(subject)
    if (user == null) {
      // Linking on email alone could let a provider identity take over a local account.
      if (users.findByEmail(email) != null)
          fail("This email already has an account. Sign in using its original method")
      user = users.saveAndFlush(User(email = email, googleSubject = subject))
      players.saveAndFlush(
          Player(name = (remote.fullName ?: email.substringBefore('@')).take(100), user = user)
      )
    }
    return LocalGooglePrincipal(
        remote.authorities,
        remote.idToken,
        remote.userInfo,
        user.id.toString(),
    )
  }

  private fun fail(message: String): Nothing =
      throw OAuth2AuthenticationException(OAuth2Error("invalid_identity"), message)
}

class LocalGooglePrincipal(
    authorities: Collection<GrantedAuthority>,
    idToken: OidcIdToken,
    userInfo: OidcUserInfo?,
    private val localUserId: String,
) : DefaultOidcUser(authorities, idToken, userInfo, "sub") {
  override fun getName(): String = localUserId
}
