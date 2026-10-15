CREATE TABLE reform
(
    id              serial PRIMARY KEY,
    base_id         varchar(48) NOT NULL UNIQUE,
    navn            text        NOT NULL,
    dato            date        NOT NULL,
    created_at_utc  timestamp   NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at_utc  timestamp   NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    archived_at_utc timestamp
);

CREATE TABLE reform_part
(
    id             serial PRIMARY KEY,
    reform_id      integer   NOT NULL REFERENCES reform (id) ON DELETE CASCADE,
    name           text      NOT NULL,
    created_at_utc timestamp NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at_utc timestamp NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    data           jsonb     NOT NULL
);

CREATE TYPE reform_execution_status AS enum (
    'prepared',
    'sent', 'send_rejected', 'send_unknown',
    'start_rejected', 'start_unknown', 'start_accepted'
    );

CREATE TABLE reform_execution
(
    id              bigserial PRIMARY KEY,
    reform_id       integer                 NOT NULL REFERENCES reform (id) ON DELETE RESTRICT,
    external_id     varchar(64)             NOT NULL UNIQUE,
    status          reform_execution_status NOT NULL,
    source_snapshot jsonb                   NOT NULL,
    payload         text                    NOT NULL,
    send_status     integer,
    send_message    text,
    start_status    integer,
    start_message   text,
    created_at_utc  timestamp               NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at_utc  timestamp               NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
