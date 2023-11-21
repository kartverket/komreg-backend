CREATE TABLE tilbakeføringsstatus
(
    id varchar(255) PRIMARY KEY NOT NULL,
    kjoringid serial NOT NULL,
    sink         varchar(255)             NOT NULL,
    opprettinger varchar(255) NOT NULL,
    endringer    varchar(255) NOT NULL,
    created_at   timestamp DEFAULT current_timestamp,
    updated_at   timestamp

);


ALTER TABLE tilbakeføringsstatus
    ADD CONSTRAINT fk_kjoring
        FOREIGN KEY (kjoringid)
            REFERENCES kjoring (id);
