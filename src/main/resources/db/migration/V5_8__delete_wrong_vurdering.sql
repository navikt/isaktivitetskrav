DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '5c88df79-04b1-4214-aa74-c01b8a4e426d';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '67212653-2d39-42ee-9bd0-69dd702b3a3e';
