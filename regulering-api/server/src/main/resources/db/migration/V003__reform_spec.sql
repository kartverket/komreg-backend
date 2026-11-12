DROP TABLE reform_cadastral_unit_parcel;
DROP TABLE reform_cadastral_unit;
DROP TABLE reform;

DROP DOMAIN title_number;
DROP DOMAIN land_number;
DROP DOMAIN municipality_number;

-- spec er kilden til sannhet. name og date avledes fra specen ved lagring, slik at listing ikke må lese den.
CREATE TABLE reform
(
    id             serial PRIMARY KEY,
    spec           jsonb     NOT NULL,
    name           text      NOT NULL,
    date           date      NOT NULL,
    created_at_utc timestamp NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at_utc timestamp NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
