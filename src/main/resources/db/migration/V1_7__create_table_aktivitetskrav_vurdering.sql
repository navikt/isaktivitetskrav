ALTER TABLE AKTIVITETSKRAV
DROP
COLUMN updated_by;
ALTER TABLE AKTIVITETSKRAV
DROP
COLUMN beskrivelse;

CREATE TABLE AKTIVITETSKRAV_VURDERING
(
    id                SERIAL PRIMARY KEY,
    uuid              CHAR(36)    NOT NULL UNIQUE,
    aktivitetskrav_id INTEGER REFERENCES AKTIVITETSKRAV (id) ON DELETE CASCADE,
    created_at        timestamptz NOT NULL,
    created_by        VARCHAR(7)  NOT NULL,
    status            VARCHAR(20) NOT NULL,
    beskrivelse       TEXT
);

CREATE INDEX IX_VURDERING_AKTIVITETSKRAV_ID on AKTIVITETSKRAV_VURDERING (aktivitetskrav_id);
