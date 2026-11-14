ALTER TABLE whatsapp_notifications
  DROP INDEX uq_whatsapp_entry,
  ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'DRAW_RESULT',
  ADD COLUMN scheduled_draw_at DATETIME(6) NULL,
  ADD CONSTRAINT uq_whatsapp_entry_type UNIQUE (ss_player_id, type);
