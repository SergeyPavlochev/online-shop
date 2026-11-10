--liquibase formatted sql
--changeset Pavlochev.SY:add-missing-outbox-columns

ALTER TABLE outbox_events ADD COLUMN updated_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN error_message VARCHAR(1024);
