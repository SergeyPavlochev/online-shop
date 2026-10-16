DROP TABLE IF EXISTS app_users CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;

CREATE TABLE app_users
(
    id    UUID         NOT NULL,
    email      VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    phone      VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uc_app_users_email UNIQUE (email)
);

CREATE TABLE outbox_events
(
    id             UUID         NOT NULL,
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP WITHOUT TIME ZONE,
    status         VARCHAR(255) NOT NULL,
    error_message  VARCHAR(1024),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uc_outbox_events_event_id UNIQUE (event_id),
    CONSTRAINT uc_outbox_events_aggregate_type_aggregate_id UNIQUE (aggregate_type, aggregate_id)
);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
