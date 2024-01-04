UPDATE aktivitetskrav
SET stoppunkt_at = stoppunkt_at - INTERVAL '1 day', updated_at = now()
WHERE created_at > '2023-12-07' AND referanse_tilfelle_bit_uuid IS NOT NULL AND status != 'AUTOMATISK_OPPFYLT';
