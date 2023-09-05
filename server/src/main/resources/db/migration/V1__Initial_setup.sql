ALTER DATABASE "komreg-db" SET search_path TO komreg;

CREATE TABLE regulering (
    id VARCHAR(255) PRIMARY KEY,
    regulering JSONB NOT NULL,
    ikrafttredelsesdato DATE NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL,
    endret TIMESTAMP WITH TIME ZONE NOT NULL,
    opprettetav VARCHAR(255) NOT NULL
);

CREATE TABLE kjoring (
    id SERIAL PRIMARY KEY,
    regulering VARCHAR(255) NOT NULL,
    start TIMESTAMP WITH TIME ZONE NOT NULL,
    slutt TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transformasjon (
    transformasjonsId JSONB PRIMARY KEY,
    kjoring INT NOT NULL,
    transformasjon JSONB NOT NULL,
    tid TIMESTAMP WITH TIME ZONE NOT NULL
);


ALTER TABLE kjoring ADD CONSTRAINT fk_regulering
    FOREIGN KEY (regulering) REFERENCES regulering(id);

ALTER TABLE transformasjon ADD CONSTRAINT fk_kjoring
    FOREIGN KEY (kjoring) REFERENCES kjoring(id);
