DELETE FROM aktivitetskrav_vurdering WHERE uuid = '60bf330b-9656-4b25-bc88-1ff1f1cae2f8';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = '10ae0e9b-f4fe-4b27-a519-9ce43fe2e5fd';
