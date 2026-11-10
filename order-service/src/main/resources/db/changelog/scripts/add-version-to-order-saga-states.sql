--liquibase formatted sql

-- changeset sergei:add-version-to-order-saga-states
-- comment: Add @Version column for optimistic locking

ALTER TABLE order_saga_states ADD COLUMN version INTEGER DEFAULT 0;
