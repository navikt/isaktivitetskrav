DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '3566834c-6f0d-46fd-9e38-6f206d066275';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'cd591d72-fc72-4221-93fc-6bb28586784a';
