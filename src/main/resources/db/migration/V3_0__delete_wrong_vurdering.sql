DELETE FROM aktivitetskrav_vurdering WHERE uuid = '28ace0c2-6b49-4fa4-8eb8-0ee9bc31c49e';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = 'ea610d00-efc0-4d8f-beb6-cf40aea079c9';
