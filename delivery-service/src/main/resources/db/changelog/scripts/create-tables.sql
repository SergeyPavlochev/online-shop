--liquibase formatted sql
--changeset Pavlochev.SY:create_tables

DROP TABLE IF EXISTS couriers CASCADE;
DROP TABLE IF EXISTS delivery_slots CASCADE;
DROP TABLE IF EXISTS slot_reservations CASCADE;

CREATE TABLE couriers
(
    id              UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    zone            VARCHAR(255) NOT NULL,
    max_orders_slot INTEGER      NOT NULL,
    CONSTRAINT pk_couriers PRIMARY KEY (id)
);

CREATE TABLE delivery_slots
(
    id           UUID                        NOT NULL,
    start_time   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    zone         VARCHAR(255)                NOT NULL,
    max_capacity INTEGER                     NOT NULL,
    current_load INTEGER                     NOT NULL,
    CONSTRAINT pk_delivery_slots PRIMARY KEY (id)
);

CREATE TABLE slot_reservations
(
    id         UUID         NOT NULL,
    order_id   UUID         NOT NULL,
    slot_id    UUID         NOT NULL,
    courier_id UUID         NOT NULL,
    status     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_slot_reservations PRIMARY KEY (id)
);
ALTER TABLE slot_reservations
    ADD CONSTRAINT uc_slot_reservations_orderid UNIQUE (order_id);