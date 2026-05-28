DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'a51dab7b-e13c-42f9-99af-dbfd5f3a3ded';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'b38d4871-46d7-41fa-b738-7ee4327085be';
