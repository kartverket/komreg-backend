DROP TABLE reform_execution;
DROP TYPE reform_execution_status;
DROP TABLE reform_part;
DROP TABLE reform;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE DOMAIN municipality_number AS varchar(19)
    CHECK (value ~ '^(\d{4}|\d{9,19})$');

CREATE DOMAIN land_number AS int4
    CHECK (value BETWEEN 0 AND 99999);

CREATE DOMAIN title_number AS int2
    CHECK (value BETWEEN 0 AND 9999);

CREATE TABLE reform
(
    id   serial PRIMARY KEY,
    name varchar(255) NOT NULL,
    date date         NOT NULL
);

CREATE TABLE reform_cadastral_unit
(
    id                      serial              NOT NULL,
    reform_id               int4                NOT NULL,
    municipality_number     municipality_number NOT NULL,
    land_number             land_number         NOT NULL,
    title_number            title_number,
    new_municipality_number municipality_number NOT NULL,
    new_land_number         land_number         NOT NULL,
    new_title_number        title_number,
    CONSTRAINT pk_reform_cadastral_unit PRIMARY KEY (id),
    CONSTRAINT ck_whole_land_number_or_not
        CHECK ((title_number IS NULL) = (new_title_number IS NULL)),
    CONSTRAINT ex_reform_source_cadastral_unit
        EXCLUDE USING gist (
        reform_id WITH =,
        municipality_number WITH =,
        land_number WITH =,
        (int4range(title_number, title_number + 1)) WITH &&
        ),
    CONSTRAINT ex_reform_target_cadastral_unit
        EXCLUDE USING gist (
        reform_id WITH =,
        new_municipality_number WITH =,
        new_land_number WITH =,
        (int4range(new_title_number, new_title_number + 1)) WITH &&
        ),
    CONSTRAINT fk_reform_id
        FOREIGN KEY (reform_id)
            REFERENCES reform (id)
            ON DELETE CASCADE
);

CREATE TABLE reform_cadastral_unit_parcel
(
    reform_cadastral_unit_id int4 NOT NULL,
    parcel_id                int8 NOT NULL,
    CONSTRAINT pk_reform_cadastral_unit_parcel PRIMARY KEY (reform_cadastral_unit_id, parcel_id),
    CONSTRAINT fk_reform_cadastral_unit_id
        FOREIGN KEY (reform_cadastral_unit_id)
            REFERENCES reform_cadastral_unit (id)
            ON DELETE CASCADE
);
