DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '0d97027a-35af-4aba-8b9b-408564cc81e6';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '4de2e2d6-916d-4c72-986d-6c82163e47b7';
