CREATE TABLE resources
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    type          TEXT        NOT NULL,
    payload       JSONB       NOT NULL,
    last_event_id BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ
);