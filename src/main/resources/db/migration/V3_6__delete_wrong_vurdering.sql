DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'd58cd2a2-18c1-404d-a55f-881fc7490ed1';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = 'd86af775-d05d-43b1-9497-11eb8a90b13c';

DELETE
FROM aktivitetskrav
WHERE uuid = 'fd4c7555-3051-4006-9c06-cc85d28e3a42';
