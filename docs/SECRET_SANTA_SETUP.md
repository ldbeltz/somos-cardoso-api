# Secret Santa setup

The backend is organized by auth, user, player, secretsanta and messaging domains. Entities and repositories stay in their domains.

## Local startup

1. Start MySQL 8 with an empty cardosoapidb database and restricted application user. Export SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD.
2. See .env.secret-santa.example for settings. Export variables; Spring does not automatically read .env files.
3. Run ./gradlew bootRun in the API repo (existing Java 25 toolchain). Flyway applies V1, V2 and V3; Hibernate validates the schema. Existing nonempty schemas need migration assessment; automatic baselining is disabled to avoid skipping V1.
4. Frontend: set API_INTERNAL_URL=http://127.0.0.1:8080 in .env.local, then npm ci and npm run dev. Open http://localhost:3000/auth. Use the same hostname consistently for cookies.
5. Register two or more players with distinct emails and phone numbers, e.g. (11) 99999-9999 (defaults to +55) or +14155552671. Create a draw including the creator and at least one other player, with a future local date/time. The browser sends its UTC instant.
6. Due draws run approximately every 15 seconds. The application must be running; overdue draws run after restart. There is no manual redraw endpoint.

## Google sign-in

Create a Google OAuth web client with the exact callback http://localhost:3000/login/oauth2/code/google for development, or https://YOUR_DOMAIN/login/oauth2/code/google in production. Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, APP_FRONTEND_URL and APP_GOOGLE_REDIRECT_URI on the API. Google login is unavailable without credentials. Verified identities are stored by Google subject; matching emails do not automatically link to password accounts. Google players must add their WhatsApp number before participating.

Reference: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html

## WhatsApp

Configure Meta WhatsApp Cloud API credentials, sender phone-number ID and an approved template. The template takes two body text parameters in order: draw name and recipient name. Example: In {{1}}, your Secret Santa recipient is {{2}}. Obtain recipient messaging permission before enabling delivery. Set WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_TEMPLATE_NAME, WHATSAPP_TEMPLATE_LANGUAGE, WHATSAPP_API_VERSION and WHATSAPP_ENABLED=true. Pin a version supported by your Meta app.

Delivery is disabled by default: jobs stay pending, and secrets and recipient data are not logged. Draws atomically save the ring and one notification per player. A separate worker leases jobs and retries failures with bounded backoff. SENT means Meta accepted the API request, not confirmed handset delivery. Receipts/webhooks are not implemented. A crash after Meta acceptance but before local acknowledgement can cause a duplicate message; it never triggers a new draw.

Reference: https://www.postman.com/meta/whatsapp-business-platform/request/o65u5m5/send-message-template-text

## Production and existing Docker files

Use HTTPS and SESSION_COOKIE_SECURE=true. Proxy /api/v1/*, /oauth2/* and /login/oauth2/* to the API on the same public origin. Keep MySQL and API ports private. Explicitly configure the public OAuth callback.

Next.js captures rewrite targets at build time: build with API_INTERNAL_URL reachable from its runtime. In Docker this is typically http://api:8080 rather than localhost. Dockerfile accepts that build argument.

The existing untracked .env.example and compose.prod.yaml are preserved. The optional compose.secret-santa.yaml overlay passes provider settings into the API container. It does not build or publish the changed image: build/tag the new backend and use its verified image before deploying. Review migrations and back up MySQL before production. Keep credentials outside Git.

Sessions are in memory and expire on API restart. Players, draws, assignments and jobs persist in MySQL. Registered accounts can browse draw summaries and the player directory. Only participants can read draw details, and only their own recipient is returned. Registration is open in this implementation; restrict access at the deployment boundary if family-only enrollment is required.

## Verification

Run ./gradlew test. Docker-backed MySQL checks require Docker and skip explicitly without it; skipped checks do not verify MySQL migrations. HTTP/session integration and domain tests run independently. Frontend checks: npm run lint, npx tsc --noEmit, npm run build and npm test if defined. Tests never send real WhatsApp messages or require Google credentials.

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
