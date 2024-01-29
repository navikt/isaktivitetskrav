DELETE FROM aktivitetskrav_vurdering WHERE uuid = 'f04fc511-1d03-4230-81b1-f58fa5d51b61';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = 'e3061c14-4ebd-483a-bf88-cfbe2bc4b5e0';
