DELETE FROM aktivitetskrav_vurdering WHERE uuid = 'b5c2b54c-38bc-435a-94c7-19fb82091ef1';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = 'da465e30-c172-4a6a-920c-8f0c935ca106';
