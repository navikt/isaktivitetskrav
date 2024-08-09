DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'fa210610-aa8f-4411-aa25-5d2c7e82a6eb';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '0feca0bd-e278-4bec-b5b4-705f8e40d3f0';
