-- Optional source details for each drug interaction, shown in the medication
-- safety feedback when present: the mechanism or rationale, the review or
-- action the source recommends, the source dataset version, and the date the
-- entry was last reviewed.
--
-- The existing rows are illustrative sample data with no such details, so they
-- stay NULL and the application shows "not provided by the source". Nothing
-- is filled in here: these columns are for a verified source loaded later.
ALTER TABLE drug_interactions
    ADD COLUMN mechanism          VARCHAR(1000) NULL,
    ADD COLUMN recommended_action VARCHAR(1000) NULL,
    ADD COLUMN source_version     VARCHAR(100)  NULL,
    ADD COLUMN last_reviewed_on   DATE          NULL;
