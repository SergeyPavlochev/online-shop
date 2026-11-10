--liquibase formatted sql
--changeset Pavlochev.SY:create_outbox_tables

CREATE TABLE IF NOT EXISTS outbox_events
(
    id             UUID                        NOT NULL,
    event_id       UUID                        NOT NULL UNIQUE,
    event_type     VARCHAR(255)                NOT NULL,
    aggregate_id   UUID                        NOT NULL,
    aggregate_type VARCHAR(255)                NOT NULL,
    payload        JSONB                       NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITHOUT TIME ZONE,
    status         VARCHAR(255)                NOT NULL,
    error_message  VARCHAR(1024),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS processed_events
(
    event_id     UUID                        NOT NULL,
    event_type   VARCHAR(255)                NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
