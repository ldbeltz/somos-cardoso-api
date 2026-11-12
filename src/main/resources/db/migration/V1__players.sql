CREATE TABLE players (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL,
  whatsapp_phone VARCHAR(16),
  password_hash VARCHAR(255),
  google_subject VARCHAR(255),
  CONSTRAINT uk_players_email UNIQUE (email),
  CONSTRAINT uk_players_google_subject UNIQUE (google_subject)
);
