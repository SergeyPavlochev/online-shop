--liquibase formatted sql
--changeset Pavlochev.SY:add_order_tracking_fields

ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(100);
