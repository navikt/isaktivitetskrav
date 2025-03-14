UPDATE aktivitetskrav
SET previous_aktivitetskrav_uuid = NULL,
    referanse_tilfelle_bit_uuid  = '37d3df89-b7da-40cf-9962-6e906c4a06a9'
where uuid = '55669f85-d293-46e2-a01e-3792eaa33708';

DELETE
FROM aktivitetskrav
WHERE uuid = '376c8aeb-7bc1-43f6-b130-4d316cbecf52';

