--liquibase formatted sql
--changeset Pavlochev.SY:create_tables

DROP TABLE IF EXISTS reservations CASCADE;
DROP TABLE IF EXISTS stocks CASCADE;

CREATE TABLE reservations
(
    id       UUID         NOT NULL,
    order_id UUID         NOT NULL,
    item_sku VARCHAR(255) NOT NULL,
    quantity INTEGER      NOT NULL,
    CONSTRAINT pk_reservations PRIMARY KEY (id)
);

CREATE TABLE stocks
(
    id                UUID         NOT NULL,
    sku               VARCHAR(255) NOT NULL,
    quantity          INTEGER      NOT NULL,
    reserved_quantity INTEGER      NOT NULL,
    version           BIGINT,
    CONSTRAINT pk_stocks PRIMARY KEY (id)
);

ALTER TABLE reservations
    ADD CONSTRAINT uc_reservations_orderid UNIQUE (order_id);
ALTER TABLE stocks
    ADD CONSTRAINT uc_stocks_sku UNIQUE (sku);