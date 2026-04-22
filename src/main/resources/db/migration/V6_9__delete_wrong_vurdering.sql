UPDATE aktivitetskrav
SET previous_aktivitetskrav_uuid = NULL,
    referanse_tilfelle_bit_uuid  = '6d933d06-8ffc-40de-ba78-07533d1f3765'
where uuid = 'eed3c805-65ee-4cfe-98de-27a483585b20';

DELETE
FROM aktivitetskrav
WHERE uuid = '639d3818-7e66-4ed5-ab5d-cd0239ded298';
