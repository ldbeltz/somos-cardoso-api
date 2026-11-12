CREATE TABLE ss_lists (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  draw_at DATETIME(6) NOT NULL,
  creator_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  drawn_at DATETIME(6) NULL,
  CONSTRAINT fk_ss_creator FOREIGN KEY (creator_id) REFERENCES players(id),
  INDEX ix_ss_due (status, draw_at)
);
CREATE TABLE ss_players (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ss_list_id BIGINT NOT NULL,
  player_id BIGINT NOT NULL,
  recipient_id BIGINT NULL,
  CONSTRAINT uq_ss_player UNIQUE (ss_list_id, player_id),
  CONSTRAINT fk_ss_list FOREIGN KEY (ss_list_id) REFERENCES ss_lists(id),
  CONSTRAINT fk_ss_player FOREIGN KEY (player_id) REFERENCES players(id),
  CONSTRAINT fk_ss_recipient FOREIGN KEY (recipient_id) REFERENCES players(id)
);
CREATE TABLE whatsapp_notifications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ss_player_id BIGINT NOT NULL,
  phone VARCHAR(32) NOT NULL,
  group_name VARCHAR(120) NOT NULL,
  recipient_name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_whatsapp_entry UNIQUE (ss_player_id),
  CONSTRAINT fk_whatsapp_entry FOREIGN KEY (ss_player_id) REFERENCES ss_players(id),
  INDEX ix_whatsapp_due (status, next_attempt_at)
);
