CREATE TABLE regulering (
    id SERIAL PRIMARY KEY,
    reguleringsId VARCHAR(255) NOT NULL,
    regulering JSON NOT NULL,
    ikrafttredelsesdato DATE NOT NULL,
    opprettet TIMESTAMP NOT NULL,
    endret TIMESTAMP NOT NULL,
    opprettetav VARCHAR(255) NOT NULL
);

CREATE TABLE kjoring (
    id SERIAL PRIMARY KEY,
    regulering INT NOT NULL,
    start TIMESTAMP,
    slutt TIMESTAMP
);

CREATE TABLE transformasjon (
    id SERIAL PRIMARY KEY,
    transformasjonsId JSON NOT NULL,
    kjoring INT NOT NULL,
    transformasjon JSON NOT NULL,
    tid TIMESTAMP NOT NULL
);


ALTER TABLE kjoring ADD CONSTRAINT fk_regulering
    FOREIGN KEY (regulering) REFERENCES regulering(id);

ALTER TABLE transformasjon ADD CONSTRAINT fk_kjoring
    FOREIGN KEY (kjoring) REFERENCES kjoring(id);
