DELETE FROM aktivitetskrav_vurdering WHERE uuid = '9222d187-55d6-4b38-9a83-6a6832bc60aa';

UPDATE aktivitetskrav
SET status = 'NY',
    updated_at = now()
WHERE uuid = 'ee6134d4-7428-4d56-809d-3f68357554ed';
