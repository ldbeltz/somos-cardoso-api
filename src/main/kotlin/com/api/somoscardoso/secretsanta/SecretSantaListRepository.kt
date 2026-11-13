package com.api.somoscardoso.secretsanta

import jakarta.persistence.LockModeType
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SecretSantaListRepository : JpaRepository<SecretSantaList, Long> {
  fun findAllByStatusAndDrawAtLessThanEqual(status: DrawStatus, now: Instant): List<SecretSantaList>

  fun findByInviteToken(inviteToken: String): SecretSantaList?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SecretSantaList s where s.id = :id")
  fun lockById(@Param("id") id: Long): SecretSantaList?
}
