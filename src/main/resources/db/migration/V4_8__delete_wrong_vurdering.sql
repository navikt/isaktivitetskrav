DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'fd435fb6-def6-45e4-883b-fe7a1ef95476';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '8d94dd22-e1d7-4080-9e30-e608489cb1b4';
