DELETE FROM aktivitetskrav_vurdering WHERE uuid = 'cebf1d2d-b1c4-476d-a58d-b1c7c910622f';

UPDATE aktivitetskrav
SET status = 'AVVENT', updated_at = now()
WHERE uuid = 'f550273b-0cab-4c8b-984e-f79a8a63939c';
