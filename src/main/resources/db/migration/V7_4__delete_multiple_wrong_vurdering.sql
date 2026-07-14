/* https://jira.adeo.no/browse/FAGSYSTEM-438724 */
DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '0e136ac7-9c73-45f1-aa43-bcee57013aed';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '232182da-7be5-4e01-8364-2884f8aebebe';

/* https://jira.adeo.no/browse/FAGSYSTEM-438719 */
DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '138eb4fd-8925-4207-99c4-8d925852afda';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'b4003e2d-c4f5-42fd-b364-7df7769ce364';

/* https://jira.adeo.no/browse/FAGSYSTEM-438725 */
DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = 'cbd85c9b-bb9c-4c8b-a277-be4ede849816';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = '04dc7d0e-53b1-4346-9888-984d14b2a885';

/* https://jira.adeo.no/browse/FAGSYSTEM-438768 */
DELETE
FROM aktivitetskrav_vurdering
WHERE uuid = '79d4285f-c7a4-428f-96bb-5868ad93aadf';

UPDATE aktivitetskrav
SET status     = 'NY',
    updated_at = now()
where uuid = 'ca068509-1d14-4b47-99c4-ba2950277170';
