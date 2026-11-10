--liquibase formatted sql
--changeset Pavlochev.SY:expand_order_saga_state

ALTER TABLE order_saga_states ADD COLUMN picking_completed BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE order_saga_states ADD COLUMN tracking_number VARCHAR(100);
ALTER TABLE order_saga_states ADD COLUMN active_courier_id UUID;
ALTER TABLE order_saga_states ADD COLUMN picking_started_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE order_saga_states ADD COLUMN delivered_at TIMESTAMP WITHOUT TIME ZONE;
