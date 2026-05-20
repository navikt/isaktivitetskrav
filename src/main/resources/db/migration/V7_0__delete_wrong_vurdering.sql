DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '9e7d7204-35fe-4574-82cf-ab75ea329ae7';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '5f2f7122-8e7c-400d-ad14-dbc891b83de2';
