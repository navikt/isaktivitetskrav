DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '40bdedc6-8246-468b-87cf-936faba7981d';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'c218e471-2d36-486a-9162-d059ca1d8251';
