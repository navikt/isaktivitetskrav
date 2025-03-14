UPDATE aktivitetskrav_varsel
SET published_at = NULL
WHERE uuid IN (
    SELECT v.uuid
    FROM aktivitetskrav_vurdering av
             INNER JOIN aktivitetskrav a on a.id = av.aktivitetskrav_id
             INNER JOIN public.aktivitetskrav_varsel v on av.id = v.aktivitetskrav_vurdering_id
    WHERE v.uuid in (
'6013ea1f-512a-4b1a-8852-eca534e075a0',
'5e0bf67f-7a48-474a-b807-3e81f43f58b2',
'd4d12f17-95d6-4d38-8110-4108b66201d9',
'c072c53b-6cbe-403e-a0b4-ad1a938a0bfe',
'4c4d14df-480c-446c-9523-621aae2e4e1f',
'ba33cd21-58b2-4d2b-8eb4-c8f6d58dca04',
'b433e16a-ba7c-4a04-9412-7548720fe261'
)
      AND av.status = 'FORHANDSVARSEL'
      AND a.status = 'FORHANDSVARSEL'
);
