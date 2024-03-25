DELETE FROM aktivitetskrav_vurdering WHERE uuid = 'e8ef1ab5-bce8-4280-b441-3e850c493e30';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = '73282ec3-f319-4b98-8fd9-b03372edc4cf';
