--liquibase formatted sql
--changeset Pavlochev.SY:create_tables

DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS order_saga_states CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS processed_events CASCADE;

CREATE TABLE orders
(
    id         UUID                        NOT NULL,
    user_id    UUID                        NOT NULL,
    amount     DECIMAL                     NOT NULL,
    currency   VARCHAR(3)                  NOT NULL,
    status     VARCHAR(255)                NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE order_saga_states
(
    order_id          UUID    NOT NULL,
    status            VARCHAR(255),
    payment_reserved  BOOLEAN NOT NULL,
    stock_reserved    BOOLEAN NOT NULL,
    delivery_reserved BOOLEAN NOT NULL,
    started_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_order_saga_states PRIMARY KEY (order_id)
);

CREATE TABLE outbox_events
(
    id             UUID                        NOT NULL,
    event_id       UUID                        NOT NULL,
    event_type     VARCHAR(255)                NOT NULL,
    aggregate_id   UUID                        NOT NULL,
    aggregate_type VARCHAR(255)                NOT NULL,
    payload        JSONB                       NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITHOUT TIME ZONE,
    status         VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE TABLE processed_events
(
    event_id     UUID                        NOT NULL,
    event_type   VARCHAR(255)                NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
ALTER TABLE outbox_events
    ADD CONSTRAINT uc_outbox_events_eventid UNIQUE (event_id);