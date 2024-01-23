DELETE FROM aktivitetskrav_vurdering WHERE uuid = '18d7b500-e54d-4086-ab83-1f216da3d4ac';

UPDATE aktivitetskrav
SET status = 'FORHANDSVARSEL',
    updated_at = now()
WHERE uuid = '3c69684f-3c0f-4e5a-bcdc-0a4a6ebb6a97';
