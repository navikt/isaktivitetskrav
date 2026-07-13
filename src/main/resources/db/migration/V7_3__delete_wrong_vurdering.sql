DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '44d901d9-8349-4cc8-a5df-bef9c6f9d9cb';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '43a1f430-8376-4355-9c08-4019c1b628a1';
