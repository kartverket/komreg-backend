-- Regler (ReformOp-er) for en regulering, kjørt med motoren `reform`.
CREATE TABLE reform_regulering (
    id VARCHAR(255) PRIMARY KEY,
    navn VARCHAR(255) NOT NULL,
    ikrafttredelsesdato DATE NOT NULL,
    regler JSONB NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL,
    endret TIMESTAMP WITH TIME ZONE NOT NULL,
    opprettetav VARCHAR(255) NOT NULL
);

-- En kjøring gjelder enten en DTO-regulering (`legacy`) eller en reform-regulering (`reform`).
ALTER TABLE kjoring ALTER COLUMN regulering DROP NOT NULL;
ALTER TABLE kjoring ADD COLUMN reform_regulering VARCHAR(255);
ALTER TABLE kjoring ADD CONSTRAINT fk_reform_regulering
    FOREIGN KEY (reform_regulering) REFERENCES reform_regulering(id);
ALTER TABLE kjoring ADD CONSTRAINT kjoring_en_regulering
    CHECK ((regulering IS NULL) <> (reform_regulering IS NULL));
