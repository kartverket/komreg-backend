ALTER TABLE kjoring
    ADD COLUMN mottaker varchar(255);

CREATE TABLE mottakerskjema
(
    id         serial PRIMARY KEY,
    mottaker   varchar(255) NOT NULL,
    isfree     boolean      NOT NULL,
    created_at timestamp DEFAULT current_timestamp,
    updated_at timestamp
);

INSERT INTO mottakerskjema (mottaker, isfree)
VALUES ('DB_MATRIKKEL_MOTTAKER1', FALSE);
INSERT INTO mottakerskjema (mottaker, isfree)
VALUES ('DB_MATRIKKEL_MOTTAKER2', FALSE);
