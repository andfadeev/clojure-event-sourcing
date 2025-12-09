create table events
(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL,
    payload jsonb NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);