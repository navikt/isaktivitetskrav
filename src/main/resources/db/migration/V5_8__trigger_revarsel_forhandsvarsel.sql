UPDATE aktivitetskrav_varsel
SET published_at = NULL
WHERE uuid IN (
    SELECT v.uuid
    FROM aktivitetskrav_vurdering av
             INNER JOIN aktivitetskrav a on a.id = av.aktivitetskrav_id
             INNER JOIN public.aktivitetskrav_varsel v on av.id = v.aktivitetskrav_vurdering_id
    WHERE v.published_at >= '2025-03-10T00:00:00'
      AND v.published_at <= '2025-03-12T10:04:00'
      AND av.status = 'FORHANDSVARSEL'
      AND a.status = 'FORHANDSVARSEL'
);
