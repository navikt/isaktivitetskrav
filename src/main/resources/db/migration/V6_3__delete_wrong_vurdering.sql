DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'bda969dd-bd00-4e4a-9522-cddf06a3c9f2';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'bda969dd-bd00-4e4a-9522-cddf06a3c9f2';

UPDATE aktivitetskrav
SET previous_aktivitetskrav_uuid = NULL,
    referanse_tilfelle_bit_uuid  = '6992cb8d-ac7f-435a-a569-d2400756365b'
where uuid = '9b49727c-5c79-415c-ab3c-e43af1670030';

DELETE
FROM aktivitetskrav
WHERE uuid = '8ecffd7e-b128-485a-9766-083e5d401d6a';
