CREATE TABLE runconfig
(

    id               varchar(255) PRIMARY KEY NOT NULL,
    runconfigid      varchar(255)             NOT NULL,
    sink             varchar(255)             NOT NULL,
    firstrunsuccess  boolean                  NOT NULL,
    secondrunsuccess boolean                  NOT NULL
);


ALTER TABLE kjoring
    ADD lastsuccessfullsink varchar(255) DEFAULT NULL;

ALTER TABLE kjoring
    ADD fk_runconfigid varchar(255);

ALTER TABLE kjoring
    ADD CONSTRAINT fk_runconfigid
        FOREIGN KEY (id)
            REFERENCES runconfig (id);


INSERT INTO runconfig (id, runconfigid, sink, firstrunsuccess, secondrunsuccess)
VALUES ('1', '1', 'teigEntitySink', TRUE, TRUE);