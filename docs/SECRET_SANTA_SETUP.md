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

Configure Meta WhatsApp Cloud API credentials, sender phone-number ID and approved templates. Draw-result messages take two body text parameters: draw name and assigned recipient name. Enrollment confirmations take draw name and scheduled date/time. Obtain participants' permission before enabling either delivery type. Set the shared credentials and API version, then configure each template independently. Pin a version supported by your Meta app.

Delivery is disabled by default: jobs stay pending, and secrets and recipient data are not logged. `WHATSAPP_ENABLED` is the master switch for all sends. `WHATSAPP_REGISTRATION_ENABLED` opts in to enrollment notices; missing registration configuration does not disable configured result delivery. Registration rows are canceled when the draw starts, and the dispatcher excludes them once the scheduled time arrives, preventing late confirmations after results. Draws atomically save the ring and one result notification per player. A separate worker leases jobs and retries failures with bounded backoff. SENT means Meta accepted the API request, not confirmed handset delivery. Receipts/webhooks are not implemented. A crash after Meta acceptance but before local acknowledgement can cause a duplicate message; it never triggers a new draw.

Reference: https://www.postman.com/meta/whatsapp-business-platform/request/o65u5m5/send-message-template-text

### Production WhatsApp configuration

The deployment workflow in `.github/workflows/ci-deploy.yaml` builds
`ldbeltz/somos-cardoso-api:latest` and triggers Coolify. If this is the deployment
used by your server, open the existing backend Compose resource in Coolify and
update its Compose definition from `compose.prod.yaml`. That file includes the
server's MySQL and API services, references the WhatsApp variables in
`api.environment`, and joins the API to both the default database network and
the external `coolify` network. Keep the existing Coolify resource/project and
`mysql_data` volume so the deployment continues using the same database.

Add or update the variables in `.env.whatsapp.example` under **Environment
Variables**. These are runtime values, not Docker build arguments. Enter the
access token directly in Coolify, keep existing database and authentication
settings, and save the changes. Adding Coolify variables alone is insufficient
for a Compose stack: the Compose definition must reference them. Redeploy the
resource to apply both changes.

The configured result template is `amigo_secreto_resultado`, language `pt_BR`,
using sender Phone Number ID `1499882366535367`. Its positional body parameters
are `{{1}}` = draw name and `{{2}}` = assigned recipient name. The fixed text
header shown in the template preview does not require a header parameter.

Keep `WHATSAPP_ENABLED=false` while the template is pending. Once Meta approves
it, confirm the sender is registered, the token can send messages for that
WhatsApp account, the account has its required payment method, and participants
have agreed to receive notifications. Before enabling, review existing pending
notifications: enabling the dispatcher can send those as well. Set
`WHATSAPP_ENABLED=true` and redeploy when ready. An HTTP acceptance is not a
delivery receipt; confirm receipt on a consenting test participant's phone.

Enrollment uses the independent `amigo_secreto_inscricao` template with
language `pt_PT` (Meta's `Portuguese (POR)` label). Its body parameters are
`{{1}}` = draw name and `{{2}}` = scheduled date/time formatted as
`dd/MM/yyyy HH:mm` in `America/Maceio` by default. Set
`WHATSAPP_REGISTRATION_TIME_ZONE` to another IANA timezone if needed. The fixed
text header `Inscrição Amigo Secreto` has no dynamic parameters. The notice
contains no recipient assignment. Set
`WHATSAPP_REGISTRATION_TEMPLATE_NAME`,
`WHATSAPP_REGISTRATION_TEMPLATE_LANGUAGE`, and
`WHATSAPP_REGISTRATION_ENABLED=true` after Meta approves this template and
participants have agreed to receive enrollment notices. New memberships get a
confirmation at draw creation, account join, or guest invite registration;
unchanged memberships do not enqueue another notice. There is currently no
draw schedule update endpoint.

`WHATSAPP_API_VERSION=v23.0` matches the existing backend default. Confirm it is
supported by the Meta app, or set a supported version explicitly.

The result template settings (`WHATSAPP_TEMPLATE_NAME` and
`WHATSAPP_TEMPLATE_LANGUAGE`) apply only to draw results. Enrollment uses the
separate `WHATSAPP_REGISTRATION_TEMPLATE_NAME` and
`WHATSAPP_REGISTRATION_TEMPLATE_LANGUAGE` settings described above.

For Docker deployments outside Coolify, merge the example's variables into the
existing API service environment, or copy it to the ignored `.env.whatsapp`
file and pass it using the API service's `env_file` setting. Spring does not
automatically load the file. Restrict a secret-bearing file with
`chmod 600 .env.whatsapp`. After changes, recreate the API container with the
existing complete deployment configuration. `compose.prod.yaml` now includes
the API image and database connection. For a manual deployment of that same
stack, merge the WhatsApp entries into its existing `.env`, retain the database
credentials, and run `docker compose -f compose.prod.yaml config --quiet`, then
`docker compose -f compose.prod.yaml up -d --no-deps --force-recreate api` from
the existing deployment directory/project. The external `coolify` network and
healthy MySQL service must already exist. For a Coolify-managed stack, use
Coolify's deployment controls instead.

References: [Coolify service environment variables](https://coolify.io/docs/services/configuration/environment-variables),
[Docker container environment variables](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/).

## Production and existing Docker files

Use HTTPS and SESSION_COOKIE_SECURE=true. Proxy /api/v1/*, /oauth2/* and /login/oauth2/* to the API on the same public origin. Keep MySQL and API ports private. Explicitly configure the public OAuth callback.

Next.js captures rewrite targets at build time: build with API_INTERNAL_URL reachable from its runtime. In Docker this is typically http://api:8080 rather than localhost. Dockerfile accepts that build argument.

The production compose.prod.yaml matches the supplied server stack, with WhatsApp runtime settings and a shared database network added. The optional compose.secret-santa.yaml overlay also passes Google and frontend settings into the API container; populate those variables if using it. Compose does not build or publish the changed image: build/tag the new backend and use its verified image before deploying. Review migrations and back up MySQL before production. Keep credentials outside Git.

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
