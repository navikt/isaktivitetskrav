ALTER TABLE AKTIVITETSKRAV_VURDERING
DROP COLUMN tilfelle_start;

ALTER TABLE AKTIVITETSKRAV_VURDERING
ADD COLUMN updated_by VARCHAR(7);

ALTER TABLE AKTIVITETSKRAV_VURDERING
ADD COLUMN stoppunkt_at DATE;
