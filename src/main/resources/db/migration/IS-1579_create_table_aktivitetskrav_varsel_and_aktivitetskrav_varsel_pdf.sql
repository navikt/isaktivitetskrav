CREATE TABLE AKTIVITETSKRAV_VARSEL
(
    id                          SERIAL PRIMARY KEY,
    uuid                        CHAR(36) NOT NULL UNIQUE,
    aktivitetskrav_vurdering_id INTEGER REFERENCES AKTIVITETSKRAV_VURDERING (id) ON DELETE CASCADE,
    document                    JSONB    NOT NULL DEFAULT '[]'::jsonb,
    journalpost_id              VARCHAR(20)
);

CREATE INDEX IX_AKTIVITETSKRAV_VARSEL_AKTIVITETSKRAV_VURDERING_ID on AKTIVITETSKRAV_VARSEL (aktivitetskrav_vurdering_id);

CREATE TABLE AKTIVITETSKRAV_VARSEL_PDF
(
    id                       SERIAL PRIMARY KEY,
    aktivitetskrav_varsel_id INTEGER REFERENCES AKTIVITETSKRAV_VARSEL (id) ON DELETE CASCADE,
    uuid                     VARCHAR(50) NOT NULL UNIQUE,
    pdf                      bytea       NOT NULL
);

CREATE INDEX IX_AKTIVITETSKRAV_VARSEL_PDF_AKTIVITETSKRAV_VARSEL_ID on AKTIVITETSKRAV_VARSEL_PDF (aktivitetskrav_varsel_id);
