--liquibase formatted sql
--changeset Pavlochev.SY:add_failure_reason_to_saga_state

ALTER TABLE order_saga_states ADD COLUMN failure_reason VARCHAR(500);
