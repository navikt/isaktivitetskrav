UPDATE aktivitetskrav
SET status     = 'LUKKET',
    updated_at = now()
where uuid = '4de2e2d6-916d-4c72-986d-6c82163e47b7';
