--liquibase formatted sql
--changeset Pavlochev.SY:create_tables

DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS payment_holds CASCADE;
DROP TABLE IF EXISTS processed_events CASCADE;

CREATE TABLE accounts
(
    id               UUID       NOT NULL,
    user_id          UUID       NOT NULL,
    balance          DECIMAL    NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    reserved_balance DECIMAL    NOT NULL,
    version          BIGINT,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);
ALTER TABLE accounts
    ADD CONSTRAINT uc_accounts_userid UNIQUE (user_id);

CREATE TABLE outbox_events
(
    id             UUID         NOT NULL,
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITHOUT TIME ZONE,
    status         VARCHAR(255) NOT NULL,
    error_message  VARCHAR(1024),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uc_outbox_events_event_id UNIQUE (event_id),
    CONSTRAINT uc_outbox_events_aggregate_type_aggregate_id UNIQUE (aggregate_type, aggregate_id)
);

CREATE TABLE payment_holds
(
    id       UUID         NOT NULL,
    order_id UUID         NOT NULL,
    user_id  UUID         NOT NULL,
    currency VARCHAR(255) NOT NULL,
    amount   DECIMAL      NOT NULL,
    status   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_payment_holds PRIMARY KEY (id)
);
ALTER TABLE payment_holds
    ADD CONSTRAINT uc_payment_holds_orderid UNIQUE (order_id);

CREATE TABLE processed_events
(
    event_id     UUID                        NOT NULL,
    event_type   VARCHAR(255)                NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);