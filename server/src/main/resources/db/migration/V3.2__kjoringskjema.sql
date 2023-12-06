ALTER TABLE kjoring
    ADD COLUMN skjema varchar(255) NOT NULL;

CREATE TABLE skjemaconfig
(
    id         serial PRIMARY KEY,
    skjema     varchar(255) NOT NULL,
    created_at timestamp DEFAULT current_timestamp,
    updated_at timestamp
);

