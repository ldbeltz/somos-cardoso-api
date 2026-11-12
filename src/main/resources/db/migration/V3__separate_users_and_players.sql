-- Keep existing players and all Secret Santa foreign keys stable.
CREATE TABLE users (
  id CHAR(36) NOT NULL PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255),
  google_subject VARCHAR(255),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT uk_users_google_subject UNIQUE (google_subject)
);
ALTER TABLE players ADD COLUMN user_id CHAR(36) NULL;
UPDATE players SET user_id = UUID() WHERE user_id IS NULL;
INSERT INTO users (id, email, password_hash, google_subject)
SELECT user_id, email, password_hash, google_subject FROM players;
ALTER TABLE players
  MODIFY COLUMN user_id CHAR(36) NOT NULL,
  ADD CONSTRAINT uk_players_user UNIQUE (user_id),
  ADD CONSTRAINT fk_players_user FOREIGN KEY (user_id) REFERENCES users(id),
  CHANGE COLUMN whatsapp_phone phone_number VARCHAR(16) NULL,
  DROP INDEX uk_players_email,
  DROP INDEX uk_players_google_subject,
  DROP COLUMN email,
  DROP COLUMN password_hash,
  DROP COLUMN google_subject;
