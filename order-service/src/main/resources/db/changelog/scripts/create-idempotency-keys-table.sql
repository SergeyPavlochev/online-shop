--liquibase formatted sql
--changeset Pavlochev.SY:create_idempotency_keys_table

CREATE TABLE idempotency_keys
(
    key        UUID        NOT NULL,
    order_id   UUID        NOT NULL,
    status     VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (key)
);

ALTER TABLE idempotency_keys
    ADD CONSTRAINT uc_idempotency_keys_key UNIQUE (key);
