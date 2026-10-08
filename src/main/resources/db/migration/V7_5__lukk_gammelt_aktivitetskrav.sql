UPDATE aktivitetskrav
SET status     = 'LUKKET',
    updated_at = now()
where uuid = '4b0a122d-98fa-45cb-9bd3-c0fb1aa6f2a1';
