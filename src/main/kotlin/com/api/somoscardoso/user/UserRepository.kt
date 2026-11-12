package com.api.somoscardoso.user

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, UUID> {
  fun findByEmail(email: String): User?

  fun findByGoogleSubject(googleSubject: String): User?
}
