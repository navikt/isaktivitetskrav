UPDATE aktivitetskrav_varsel
SET published_at = NULL,svarfrist='2025-04-09'
WHERE uuid IN (
    SELECT v.uuid
    FROM aktivitetskrav_vurdering av
             INNER JOIN aktivitetskrav a on a.id = av.aktivitetskrav_id
             INNER JOIN public.aktivitetskrav_varsel v on av.id = v.aktivitetskrav_vurdering_id
    WHERE v.published_at >= '2025-02-27T11:59:00Z'
      AND v.published_at <= '2025-03-10T00:00:00Z'
      AND av.status = 'FORHANDSVARSEL'
      AND a.status = 'FORHANDSVARSEL'
);
