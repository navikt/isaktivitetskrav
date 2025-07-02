UPDATE aktivitetskrav
SET previous_aktivitetskrav_uuid = NULL,
    referanse_tilfelle_bit_uuid  = 'bd2aa2ae-1e54-4aaa-8018-88ffd6717676'
where uuid = '775d5719-d79d-4cd1-8167-d9ec5ec898d0';

DELETE
FROM aktivitetskrav
WHERE uuid = '3ea836bd-9f22-44fb-8494-f91e9279d731';
