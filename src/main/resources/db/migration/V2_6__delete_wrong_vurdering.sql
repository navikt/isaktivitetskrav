DELETE FROM aktivitetskrav_vurdering WHERE uuid = '88f77eae-c347-488e-b279-fbfbc2ed853d';

UPDATE aktivitetskrav
SET status = 'AVVENT',
    updated_at = now()
WHERE uuid = 'ff086105-e5ff-4134-9812-2caadcf7205f';
