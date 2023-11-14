CREATE TABLE runconfig
(
    id               varchar(255) PRIMARY KEY NOT NULL,
    kjoringid        serial  NOT NULL,
    sink             varchar(255)             NOT NULL,
    firstrunsuccess  boolean                  NOT NULL,
    secondrunsuccess boolean NOT NULL,
    created_at       timestamp DEFAULT current_timestamp,
    updated_at       timestamp

);


ALTER TABLE runconfig
    ADD CONSTRAINT fk_kjoring
        FOREIGN KEY (kjoringid)
            REFERENCES kjoring (id);



INSERT INTO runconfig (id, kjoringid, sink, firstrunsuccess, secondrunsuccess)
VALUES ('a1a36a10-4da1-4472-ab77-3c0eeaef590e', 14, 'teigEntitySink', FALSE, FALSE);

INSERT INTO runconfig (id, kjoringid, sink, firstrunsuccess, secondrunsuccess)
VALUES ('c336a10-4da1-4472-ab77-3c0eafeef590e', 14, 'matrikkelEnhetSink', FALSE, FALSE);
