package com.api.somoscardoso.secretsanta

import com.api.somoscardoso.messaging.NotificationType
import com.api.somoscardoso.messaging.WhatsAppNotification
import com.api.somoscardoso.messaging.WhatsAppNotificationRepository
import com.api.somoscardoso.player.Player
import com.api.somoscardoso.player.PlayerRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class CreateSecretSanta(
    val name: String,
    val drawAt: Instant,
    val playerIds: List<UUID> = emptyList(),
)

data class ChangeDrawDate(val drawAt: Instant)

data class AddSecretSantaPlayer(val playerId: UUID)

data class InviteRegistration(val name: String, val phoneNumber: String)

data class InviteInfo(
    val id: Long,
    val name: String,
    val drawAt: Instant,
    val status: DrawStatus,
    val secretWord: String? = null,
)

data class RevealRecipientRequest(val word: String)

data class PlayerName(val id: UUID, val name: String)

data class SecretSantaSummary(
    val id: Long,
    val name: String,
    val drawAt: Instant,
    val status: DrawStatus,
    val creatorId: UUID,
    val playerCount: Int,
    val isParticipant: Boolean,
)

data class SecretSantaDetail(
    val id: Long,
    val name: String,
    val drawAt: Instant,
    val status: DrawStatus,
    val creatorId: UUID,
    val playerCount: Int,
    val isParticipant: Boolean,
    val players: List<PlayerName>,
    val myRecipient: PlayerName?,
    val secretWord: String?,
    val notificationStatus: String?,
    val inviteToken: String,
)

@Service
@Transactional
class SecretSantaService(
    private val lists: SecretSantaListRepository,
    private val entries: SecretSantaPlayerRepository,
    private val players: PlayerRepository,
    private val notifications: WhatsAppNotificationRepository,
    private val draw: CircularDraw,
    private val clock: Clock,
    private val wordPool: SecretSantaWordRepository? = null,
) {
  private fun fail(status: HttpStatus, message: String): Nothing =
      throw ResponseStatusException(status, message)

  private fun player(subject: String): Player {
    val userId =
        try {
          UUID.fromString(subject)
        } catch (_: IllegalArgumentException) {
          fail(HttpStatus.UNAUTHORIZED, "Sign in to continue")
        }
    return players.findByUserId(userId) ?: fail(HttpStatus.UNAUTHORIZED, "Sign in to continue")
  }

  private fun locked(id: Long) =
      lists.lockById(id) ?: fail(HttpStatus.NOT_FOUND, "Secret Santa not found")

  private fun editable(list: SecretSantaList) {
    if (list.status != DrawStatus.SCHEDULED || !list.drawAt.isAfter(clock.instant()))
        fail(
            HttpStatus.CONFLICT,
            "This invite is closed. Players can only join a scheduled draw before its draw time.",
        )
  }

  private fun validPhone(player: Player) {
    if (player.phoneNumber?.matches(Regex("^\\+[1-9]\\d{7,14}$")) != true)
        fail(HttpStatus.BAD_REQUEST, "Every player needs a valid WhatsApp number before joining")
  }

  private fun availableWords(): List<String> =
      wordPool
          ?.findAll()
          ?.map { it.word }
          ?.distinctBy { it.lowercase() }
          ?.takeIf { it.isNotEmpty() }
          ?: listOf("abacate", "alecrim", "amora", "arara", "brisa", "caju", "canela", "capivara")

  private fun assignWords(count: Int, alreadyUsed: Set<String> = emptySet()): List<String> {
    val used = alreadyUsed.map { it.lowercase() }.toSet()
    val available = availableWords().filter { it.lowercase() !in used }.shuffled()
    if (available.size < count)
        fail(HttpStatus.CONFLICT, "No unique secret words remain for this draw")
    return available.take(count)
  }

  @Transactional(readOnly = true)
  fun all(email: String): List<SecretSantaSummary> {
    val me = player(email).id
    return lists
        .findAll()
        .sortedBy { it.drawAt }
        .filter { list ->
          list.creatorId == me || entries.findBySsListIdAndPlayerId(list.id!!, me) != null
        }
        .map { list ->
          val members = entries.findAllBySsListIdOrderByIdAsc(list.id!!)
          SecretSantaSummary(
              list.id!!,
              list.name,
              list.drawAt,
              list.status,
              list.creatorId,
              members.size,
              members.any { it.playerId == me },
          )
        }
  }

  fun create(email: String, request: CreateSecretSanta): SecretSantaDetail {
    val me = player(email)
    val name = request.name.trim()
    if (name.length !in 1..120)
        fail(HttpStatus.BAD_REQUEST, "Name must contain 1 to 120 characters")
    if (!request.drawAt.isAfter(clock.instant()))
        fail(HttpStatus.BAD_REQUEST, "Choose a future date and time")
    if (request.playerIds.size > 100)
        fail(HttpStatus.BAD_REQUEST, "A draw supports at most 100 players")
    if (request.playerIds.distinct().size != request.playerIds.size)
        fail(HttpStatus.BAD_REQUEST, "Players must be distinct")
    val requested = players.findAllById(request.playerIds)
    if (requested.size != request.playerIds.size)
        fail(HttpStatus.BAD_REQUEST, "One or more players do not exist")
    val selected = (requested + me).distinctBy { it.id }
    val ids = selected.map { it.id }
    if (ids.size > 100) fail(HttpStatus.BAD_REQUEST, "A draw supports at most 100 players")
    if (ids.isEmpty()) fail(HttpStatus.BAD_REQUEST, "The creator must participate")
    selected.forEach(::validPhone)
    val list =
        lists.saveAndFlush(
            SecretSantaList(
                name = name,
                drawAt = request.drawAt,
                creatorId = me.id,
                inviteToken = UUID.randomUUID().toString().replace("-", ""),
            )
        )
    val secretWords = assignWords(ids.size)
    val memberships =
        entries.saveAll(
            ids.mapIndexed { index, playerId ->
              SecretSantaPlayer(
                  ssListId = list.id!!,
                  playerId = playerId,
                  secretWord = secretWords[index],
              )
            }
        )
    val directory = selected.associateBy { it.id }
    notifications.saveAll(
        memberships.map { registrationNotification(it, list, directory.getValue(it.playerId)) }
    )
    return detail(list.id!!, email)
  }

  @Transactional(readOnly = true)
  fun inviteInfo(token: String): InviteInfo {
    val list = lists.findByInviteToken(token) ?: fail(HttpStatus.NOT_FOUND, "Invite not found")
    return InviteInfo(list.id!!, list.name, list.drawAt, list.status)
  }

  fun joinByInvite(token: String, request: InviteRegistration): InviteInfo {
    val found = lists.findByInviteToken(token) ?: fail(HttpStatus.NOT_FOUND, "Invite not found")
    val list = locked(found.id!!)
    editable(list)
    val name = request.name.trim()
    if (name.length !in 1..100)
        fail(HttpStatus.BAD_REQUEST, "Name must contain 1 to 100 characters")
    val phone = com.api.somoscardoso.player.PhoneNumbers.normalize(request.phoneNumber)
    val existing = players.findByPhoneNumber(phone)
    val guest = existing ?: players.save(Player(name = name, phoneNumber = phone))
    if (existing != null && guest.name.isBlank()) guest.name = name
    if (entries.findBySsListIdAndPlayerId(list.id!!, guest.id) != null)
        fail(HttpStatus.CONFLICT, "This phone number is already registered in this draw")
    if (entries.countBySsListId(list.id!!) >= 100) fail(HttpStatus.CONFLICT, "This draw is full")
    val usedWords =
        entries.findAllBySsListIdOrderByIdAsc(list.id!!).mapNotNull { it.secretWord }.toSet()
    val word = assignWords(1, usedWords).single()
    val membership =
        entries.save(
            SecretSantaPlayer(ssListId = list.id!!, playerId = guest.id, secretWord = word)
        )
    notifications.save(registrationNotification(membership, list, guest))
    return InviteInfo(list.id!!, list.name, list.drawAt, status = list.status, secretWord = word)
  }

  fun detail(id: Long, email: String): SecretSantaDetail {
    val me = player(email)
    val list = locked(id)
    val members = entries.findAllBySsListIdOrderByIdAsc(id)
    val mine = members.firstOrNull { it.playerId == me.id }
    if (mine == null && list.creatorId != me.id)
        fail(HttpStatus.FORBIDDEN, "Only participants or the creator can view this Secret Santa")
    val directory = players.findAllById(members.map { it.playerId }).associateBy { it.id }
    val recipient = mine?.recipientId?.let { directory[it] }?.let { PlayerName(it.id, it.name) }
    val secretWord = mine?.let { membership ->
      membership.secretWord
          ?: assignWords(
                  1,
                  members.filter { it.id != membership.id }.mapNotNull { it.secretWord }.toSet(),
              )
              .single()
              .also {
                membership.secretWord = it
                entries.save(membership)
              }
    }
    return SecretSantaDetail(
        id,
        list.name,
        list.drawAt,
        list.status,
        list.creatorId,
        members.size,
        mine != null,
        members.map {
          val member = directory.getValue(it.playerId)
          PlayerName(member.id, member.name)
        },
        recipient,
        secretWord,
        mine
            ?.let { notifications.findBySsPlayerIdAndType(it.id!!, NotificationType.DRAW_RESULT) }
            ?.status
            ?.name,
        list.inviteToken,
    )
  }

  fun changeDrawDate(id: Long, request: ChangeDrawDate) {
    val list = locked(id)
    editable(list)
    if (!request.drawAt.isAfter(clock.instant()))
        fail(HttpStatus.BAD_REQUEST, "Choose a future date and time")
    list.drawAt = request.drawAt
    lists.save(list)
    entries.findAllBySsListIdOrderByIdAsc(id).forEach { member ->
      notifications.findBySsPlayerIdAndType(member.id!!, NotificationType.REGISTRATION)?.let {
          notice ->
        if (notice.status != com.api.somoscardoso.messaging.NotificationStatus.SENT) {
          notice.scheduledDrawAt = request.drawAt
          notifications.save(notice)
        }
      }
    }
  }

  fun addPlayer(id: Long, playerId: UUID) {
    val list = locked(id)
    editable(list)
    val player =
        players.findById(playerId).orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        }
    if (entries.findBySsListIdAndPlayerId(id, playerId) != null)
        fail(HttpStatus.CONFLICT, "Player is already registered in this draw")
    if (entries.countBySsListId(id) >= 100) fail(HttpStatus.CONFLICT, "This draw is full")
    validPhone(player)
    val usedWords = entries.findAllBySsListIdOrderByIdAsc(id).mapNotNull { it.secretWord }.toSet()
    val secretWord = assignWords(1, usedWords).single()
    val membership =
        entries.saveAndFlush(
            SecretSantaPlayer(ssListId = id, playerId = playerId, secretWord = secretWord)
        )
    notifications.save(registrationNotification(membership, list, player))
  }

  fun removePlayer(id: Long, playerId: UUID) {
    val list = locked(id)
    editable(list)
    val entry =
        entries.findBySsListIdAndPlayerId(id, playerId)
            ?: fail(HttpStatus.NOT_FOUND, "Player is not registered in this draw")
    notifications.deleteAllBySsPlayerId(entry.id!!)
    entries.delete(entry)
  }

  fun delete(id: Long) {
    locked(id)
    entries.findAllBySsListIdOrderByIdAsc(id).forEach { member ->
      notifications.deleteAllBySsPlayerId(member.id!!)
      entries.delete(member)
    }
    lists.deleteById(id)
  }

  fun join(id: Long, email: String): SecretSantaDetail {
    val list = locked(id)
    editable(list)
    val me = player(email)
    validPhone(me)
    if (entries.findBySsListIdAndPlayerId(id, me.id) == null) {
      if (entries.countBySsListId(id) >= 100) fail(HttpStatus.CONFLICT, "This draw is full")
      val usedWords = entries.findAllBySsListIdOrderByIdAsc(id).mapNotNull { it.secretWord }.toSet()
      val secretWord = assignWords(1, usedWords).single()
      val membership =
          entries.saveAndFlush(
              SecretSantaPlayer(ssListId = id, playerId = me.id, secretWord = secretWord)
          )
      notifications.save(registrationNotification(membership, list, me))
    }
    return detail(id, email)
  }

  @Transactional(readOnly = true)
  fun revealRecipient(id: Long, request: RevealRecipientRequest): PlayerName {
    val list =
        lists.findById(id).orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND, "Secret Santa not found")
        }
    if (list.status != DrawStatus.DRAWN)
        fail(HttpStatus.CONFLICT, "The recipient can only be revealed after the draw")
    val word = request.word.trim().lowercase()
    if (word.isBlank()) fail(HttpStatus.BAD_REQUEST, "Enter your secret word")
    val membership =
        entries.findBySsListIdAndSecretWord(id, word)
            ?: fail(HttpStatus.FORBIDDEN, "The secret word is incorrect")
    val recipientId =
        membership.recipientId ?: fail(HttpStatus.CONFLICT, "Recipient is not available")
    val recipient =
        players.findById(recipientId).orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found")
        }
    return PlayerName(recipient.id, recipient.name)
  }

  fun leave(id: Long, email: String) {
    val list = locked(id)
    editable(list)
    val me = player(email)
    val entry =
        entries.findBySsListIdAndPlayerId(id, me.id)
            ?: fail(HttpStatus.NOT_FOUND, "You are not registered in this draw")
    notifications.deleteAllBySsPlayerId(entry.id!!)
    entries.delete(entry)
  }

  /** Locks the list so competing schedulers and registrations cannot change a draw. */
  fun drawIfDue(id: Long): Boolean {
    val list = locked(id)
    val now = clock.instant()
    if (list.status != DrawStatus.SCHEDULED || list.drawAt.isAfter(now)) return false
    val members = entries.findAllBySsListIdOrderByIdAsc(id)
    members.forEach { member ->
      notifications.findBySsPlayerIdAndType(member.id!!, NotificationType.REGISTRATION)?.let {
          notice ->
        if (notice.status != com.api.somoscardoso.messaging.NotificationStatus.SENT) {
          notice.status = com.api.somoscardoso.messaging.NotificationStatus.CANCELLED
          notifications.save(notice)
        }
      }
    }
    if (members.size < 2) {
      list.status = DrawStatus.NOT_DRAWN
      list.drawnAt = now
      lists.save(list)
      return true
    }
    val directory = players.findAllById(members.map { it.playerId }).associateBy { it.id }
    val assignments = draw.assign(members.map { it.playerId })
    members.forEach { entry ->
      entry.recipientId = assignments.getValue(entry.playerId)
      notifications.save(
          WhatsAppNotification(
              ssPlayerId = entry.id!!,
              phone = directory.getValue(entry.playerId).phoneNumber!!,
              groupName = list.name,
              recipientName = directory.getValue(entry.recipientId!!).name,
              type = NotificationType.DRAW_RESULT,
              nextAttemptAt = now,
          )
      )
    }
    entries.saveAll(members)
    list.status = DrawStatus.DRAWN
    list.drawnAt = now
    lists.save(list)
    return true
  }

  private fun registrationNotification(
      membership: SecretSantaPlayer,
      list: SecretSantaList,
      participant: Player,
  ) =
      WhatsAppNotification(
          ssPlayerId = requireNotNull(membership.id),
          phone = requireNotNull(participant.phoneNumber),
          groupName = list.name,
          type = NotificationType.REGISTRATION,
          scheduledDrawAt = list.drawAt,
          nextAttemptAt = clock.instant(),
      )
}
