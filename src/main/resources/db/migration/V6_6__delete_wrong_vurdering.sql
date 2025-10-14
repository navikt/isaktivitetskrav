UPDATE aktivitetskrav
SET previous_aktivitetskrav_uuid = NULL,
    referanse_tilfelle_bit_uuid  = '8e9d6f68-0fce-4e17-9258-0372e2a7e308'
where uuid = '12944bbe-d897-450b-a59c-430d30ee4d2f';

DELETE
FROM aktivitetskrav
WHERE uuid = 'c96d5188-f349-4f5c-8a0b-76ead095d4af';
