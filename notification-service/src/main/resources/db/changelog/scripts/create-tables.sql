--liquibase formatted sql
--changeset Pavlochev.SY:create_tables

DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS processed_events CASCADE;

CREATE TABLE notifications
(
    id         UUID                        NOT NULL,
    user_id    UUID                        NOT NULL,
    type       VARCHAR(255)                NOT NULL,
    order_id   UUID                        NOT NULL,
    amount     VARCHAR(255)                NOT NULL,
    message    VARCHAR(500)                NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE TABLE processed_events
(
    event_id     UUID                        NOT NULL,
    event_type   VARCHAR(255)                NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);