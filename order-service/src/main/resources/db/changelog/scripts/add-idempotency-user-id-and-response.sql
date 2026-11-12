--liquibase formatted sql
--changeset Pavlochev.SY:add_idempotency_user_id_and_response

ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    ADD COLUMN IF NOT EXISTS response_body JSONB,
    ADD COLUMN IF NOT EXISTS response_status_code INTEGER;

UPDATE idempotency_keys
    SET status = 'IN_PROGRESS'
    WHERE status = 'PROCESSING';
UPDATE idempotency_keys
    SET status = 'SUCCESS'
    WHERE status = 'COMPLETED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_key_user ON idempotency_keys (key, user_id);
