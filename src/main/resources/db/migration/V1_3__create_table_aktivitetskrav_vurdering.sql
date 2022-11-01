CREATE TABLE AKTIVITETSKRAV_VURDERING
(
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    personident        VARCHAR(11) NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    status             VARCHAR(20) NOT NULL,
    beskrivelse        TEXT,
    tilfelle_start     DATE        NOT NULL
);

CREATE INDEX IX_AKTIVITETSKRAV_VURDERING_PERSONIDENT on AKTIVITETSKRAV_VURDERING (personident);
