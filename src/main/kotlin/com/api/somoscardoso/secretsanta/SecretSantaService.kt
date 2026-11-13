package com.api.somoscardoso.secretsanta

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
    val playerIds: List<Long> = emptyList(),
)

data class InviteRegistration(val name: String, val phoneNumber: String)

data class InviteInfo(val name: String, val drawAt: Instant)

data class PlayerName(val id: Long, val name: String)

data class SecretSantaSummary(
    val id: Long,
    val name: String,
    val drawAt: Instant,
    val status: DrawStatus,
    val creatorId: Long,
    val playerCount: Int,
    val isParticipant: Boolean,
)

data class SecretSantaDetail(
    val id: Long,
    val name: String,
    val drawAt: Instant,
    val status: DrawStatus,
    val creatorId: Long,
    val playerCount: Int,
    val isParticipant: Boolean,
    val players: List<PlayerName>,
    val myRecipient: PlayerName?,
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
        fail(HttpStatus.CONFLICT, "Registration has closed for this draw")
  }

  private fun validPhone(player: Player) {
    if (player.phoneNumber?.matches(Regex("^\\+[1-9]\\d{7,14}$")) != true)
        fail(HttpStatus.BAD_REQUEST, "Every player needs a valid WhatsApp number before joining")
  }

  @Transactional(readOnly = true)
  fun all(email: String): List<SecretSantaSummary> {
    val me = player(email).id!!
    return lists
        .findAll()
        .sortedBy { it.drawAt }
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
    if (request.playerIds.size > 500)
        fail(HttpStatus.BAD_REQUEST, "A draw supports at most 500 players")
    if (request.playerIds.distinct().size != request.playerIds.size)
        fail(HttpStatus.BAD_REQUEST, "Players must be distinct")
    val ids = (request.playerIds + me.id!!).distinct()
    if (ids.size > 500) fail(HttpStatus.BAD_REQUEST, "A draw supports at most 500 players")
    if (ids.isEmpty()) fail(HttpStatus.BAD_REQUEST, "The creator must participate")
    val selected = players.findAllById(ids)
    if (selected.size != ids.size) fail(HttpStatus.BAD_REQUEST, "One or more players do not exist")
    selected.forEach(::validPhone)
    val list =
        lists.saveAndFlush(
            SecretSantaList(
                name = name,
                drawAt = request.drawAt,
                creatorId = me.id!!,
                inviteToken = UUID.randomUUID().toString().replace("-", ""),
            )
        )
    entries.saveAll(ids.map { SecretSantaPlayer(ssListId = list.id!!, playerId = it) })
    return detail(list.id!!, email)
  }

  @Transactional(readOnly = true)
  fun inviteInfo(token: String): InviteInfo {
    val list = lists.findByInviteToken(token) ?: fail(HttpStatus.NOT_FOUND, "Invite not found")
    return InviteInfo(list.name, list.drawAt)
  }

  fun joinByInvite(token: String, request: InviteRegistration): InviteInfo {
    val list = lists.findByInviteToken(token) ?: fail(HttpStatus.NOT_FOUND, "Invite not found")
    editable(list)
    val name = request.name.trim()
    if (name.length !in 1..100)
        fail(HttpStatus.BAD_REQUEST, "Name must contain 1 to 100 characters")
    val phone = com.api.somoscardoso.player.PhoneNumbers.normalize(request.phoneNumber)
    val existing = players.findByPhoneNumber(phone)
    val guest = existing ?: players.save(Player(name = name, phoneNumber = phone))
    if (existing != null && guest.name.isBlank()) guest.name = name
    if (entries.findBySsListIdAndPlayerId(list.id!!, guest.id!!) != null)
        fail(HttpStatus.CONFLICT, "This phone number is already registered in this draw")
    if (entries.countBySsListId(list.id!!) >= 500) fail(HttpStatus.CONFLICT, "This draw is full")
    entries.save(SecretSantaPlayer(ssListId = list.id!!, playerId = guest.id!!))
    return InviteInfo(list.name, list.drawAt)
  }

  @Transactional(readOnly = true)
  fun detail(id: Long, email: String): SecretSantaDetail {
    val me = player(email)
    val list =
        lists.findById(id).orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND, "Secret Santa not found")
        }
    val members = entries.findAllBySsListIdOrderByIdAsc(id)
    val mine =
        members.firstOrNull { it.playerId == me.id }
            ?: fail(HttpStatus.FORBIDDEN, "Only registered players can view this Secret Santa")
    val directory = players.findAllById(members.map { it.playerId }).associateBy { it.id!! }
    val recipient = mine.recipientId?.let { directory[it] }?.let { PlayerName(it.id!!, it.name) }
    return SecretSantaDetail(
        id,
        list.name,
        list.drawAt,
        list.status,
        list.creatorId,
        members.size,
        true,
        members.map { PlayerName(it.playerId, directory.getValue(it.playerId).name) },
        recipient,
        notifications.findBySsPlayerId(mine.id!!)?.status?.name,
        list.inviteToken,
    )
  }

  fun join(id: Long, email: String): SecretSantaDetail {
    val list = locked(id)
    editable(list)
    val me = player(email)
    validPhone(me)
    if (entries.findBySsListIdAndPlayerId(id, me.id!!) == null) {
      if (entries.countBySsListId(id) >= 500) fail(HttpStatus.CONFLICT, "This draw is full")
      entries.saveAndFlush(SecretSantaPlayer(ssListId = id, playerId = me.id!!))
    }
    return detail(id, email)
  }

  fun leave(id: Long, email: String) {
    val list = locked(id)
    editable(list)
    val me = player(email)
    if (list.creatorId == me.id) fail(HttpStatus.CONFLICT, "The creator must stay in the draw")
    val entry =
        entries.findBySsListIdAndPlayerId(id, me.id!!)
            ?: fail(HttpStatus.NOT_FOUND, "You are not registered in this draw")
    if (entries.countBySsListId(id) <= 2)
        fail(HttpStatus.CONFLICT, "At least two players must remain")
    entries.delete(entry)
  }

  /** Locks the list so competing schedulers and registrations cannot change a draw. */
  fun drawIfDue(id: Long): Boolean {
    val list = locked(id)
    val now = clock.instant()
    if (list.status != DrawStatus.SCHEDULED || list.drawAt.isAfter(now)) return false
    val members = entries.findAllBySsListIdOrderByIdAsc(id)
    if (members.size < 2) {
      list.status = DrawStatus.NOT_DRAWN
      list.drawnAt = now
      lists.save(list)
      return true
    }
    val directory = players.findAllById(members.map { it.playerId }).associateBy { it.id!! }
    val assignments = draw.assign(members.map { it.playerId })
    members.forEach { entry ->
      entry.recipientId = assignments.getValue(entry.playerId)
      notifications.save(
          WhatsAppNotification(
              ssPlayerId = entry.id!!,
              phone = directory.getValue(entry.playerId).phoneNumber!!,
              groupName = list.name,
              recipientName = directory.getValue(entry.recipientId!!).name,
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
}
