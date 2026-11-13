# Secret Santa backend

## Behavior

Every authenticated user may create a draw. A draw is created with a name,
a future UTC instant, and at least two distinct registered players including
the creator. All selected players must already have a WhatsApp number in E.164
format. The creator is included automatically if omitted. Registration stays
open until the scheduled instant. Leaving cannot reduce a draw below two players;
the creator remains a player. An initial capacity of 500 players bounds requests.

A scheduled worker checks persistent due draws every 15 seconds. If the server
was offline at the draw instant, the draw is picked up after restart. Scheduling
therefore happens at or shortly after the selected instant, rather than with
hard real-time precision.

The worker locks the SSList row, securely shuffles the players, copies the order
into a java.util.LinkedList, and stores the next player's ID as each recipient.
The last points to the first. This creates one closed ring, with one giver and
one recipient for each player and no self-matches. The same transaction stores
a durable WhatsApp outbox message per assignment and marks the draw complete.
Database locks and the DRAWN state prevent redrawing and serialize registration
against the scheduled draw. The outbox has a unique key per assignment.

A player may see their own recipient once drawn, never another player's recipient.
The family can browse safe draw summaries; full membership/detail requires joining.

## API

All routes require authenticated sessions and state-changing requests require CSRF.

- GET /api/v1/secret-santas: summaries with id, name, drawAt, status, creatorId,
  playerCount, isParticipant.
- POST /api/v1/secret-santas: JSON name, drawAt, playerIds; returns 201 detail.
- GET /api/v1/secret-santas/{id}: participant-only detail with summary fields,
  players (id/name only), myRecipient or null, and the current player's
  notificationStatus or null.
- POST /api/v1/secret-santas/{id}/join: joins before the cutoff; returns detail.
- DELETE /api/v1/secret-santas/{id}/players/me: leaves before cutoff; returns 204.

Draw status is SCHEDULED, DRAWN, or NOT_DRAWN. A due draw with fewer than two
players becomes NOT_DRAWN and does not create assignments or notifications.
Notification status is PENDING, SENDING, or
SENT. SENT means the WhatsApp API accepted the request; it is not a delivery
receipt. Without credentials, the draw still takes place and messages remain
PENDING until the provider is enabled. No recipients are logged.

## WhatsApp setup

The adapter uses the official Meta WhatsApp Cloud API. Prepare a sender phone
number ID, server-side access token, and an approved text template with exactly
two body text parameters in this order: draw name, recipient name. For example:
"Your Secret Santa in {{1}} is {{2}}." Use the template's actual language code.
Obtain recipient permission to send their draw notification before enrolling them.

Spring configuration properties:

- app.whatsapp.enabled (false by default)
- app.whatsapp.access-token
- app.whatsapp.phone-number-id
- app.whatsapp.api-version (v23.0 default; configurable for the provider account)
- app.whatsapp.template-name
- app.whatsapp.template-language (en_US by default)
- app.whatsapp.poll-ms (15000 by default)
- app.draw.poll-ms (15000 by default)

Enable the adapter only with the actual approved template and valid credentials.
The provider sends POST /{version}/{phone-number-id}/messages with a template body.
Request format reference: [Meta's WhatsApp Business Platform collection](https://www.postman.com/meta/whatsapp-business-platform/request/o65u5m5/send-message-template-text).

## Retry and operational limits

Delivery happens outside the draw transaction. A dispatcher claims at most 25
messages per pass, with a five-minute lease. Errors retry with exponential delay
from 30 seconds up to six hours. Expired claims can be resumed after a process
restart. Missing configuration consumes no attempts and does not mark messages
as sent. Persistent provider errors remain pending; monitor pending counts and
credentials to avoid delayed notifications.

External delivery is at least once, not exactly once: a process crash after Meta
accepts a request but before the local status commit can cause a duplicate. A
provider acknowledgement does not establish handset delivery; delivery receipts
and a webhook-driven delivery state are not implemented. Set up and verify the
actual provider account before relying on the feature for a family event.

Tests use mock senders and never send live WhatsApp messages.

## User and Player identity upgrade (V3)

User owns authentication (UUID id, email, password hash, Google subject). Player
owns the family display name and phoneNumber, and has a unique user_id foreign
key to its User. Player numeric IDs remain stable for Secret Santa membership
and recipient relationships. The API returns id (player ID), userId (UUID), name,
email and phoneNumber. Authentication principals use the User UUID.

Registration and profile updates accept formatted Brazilian national numbers
(10 or 11 digits) and prepend +55. Bare 55 plus a national number is recognized
without duplicating the country code. Explicit + international numbers preserve
their country code. All numbers are stored in E.164 format. Existing phone
values are copied unchanged by V3; Google profiles may have no number until
updated. The request/response field is phoneNumber throughout the API.

V1 and V2 are unchanged. V3 backfills a unique UUID per existing account, copies
credentials into users, links players, and renames whatsapp_phone to phone_number.
It keeps player IDs, draws, assignments and notification jobs intact. Back up
the database and deploy matching backend/frontend versions together. V3 removes
the old authentication columns from players: rolling back only to an older
binary is insufficient; restore the matching database backup or execute a
reviewed reverse migration. In-memory sessions expire on deployment; users sign
in again with their existing credentials.
