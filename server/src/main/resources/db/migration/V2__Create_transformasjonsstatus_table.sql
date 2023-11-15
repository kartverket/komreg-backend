CREATE TABLE tilbakeføringsstatus
(
    id           varchar(255) PRIMARY KEY NOT NULL,
    reguleringid varchar(255)             NOT NULL,
    sink         varchar(255)             NOT NULL,
    opprettinger boolean,
    endringer    boolean,
    created_at   timestamp DEFAULT current_timestamp,
    updated_at   timestamp

);


ALTER TABLE tilbakeføringsstatus
    ADD CONSTRAINT fk_kjoring
        FOREIGN KEY (reguleringid)
            REFERENCES regulering (id);