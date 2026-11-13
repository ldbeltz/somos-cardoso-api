ALTER TABLE users ADD COLUMN phone_number VARCHAR(16) NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_phone_number UNIQUE (phone_number);
ALTER TABLE players MODIFY COLUMN user_id CHAR(36) NULL;
ALTER TABLE players ADD CONSTRAINT uk_players_phone_number UNIQUE (phone_number);
ALTER TABLE ss_lists ADD COLUMN invite_token VARCHAR(64) NULL;
UPDATE ss_lists SET invite_token = REPLACE(UUID(), '-', '') WHERE invite_token IS NULL;
ALTER TABLE ss_lists MODIFY COLUMN invite_token VARCHAR(64) NOT NULL;
ALTER TABLE ss_lists ADD CONSTRAINT uk_ss_lists_invite_token UNIQUE (invite_token);
