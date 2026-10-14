-- Gives the four synthetic seed patients from V3 plausible fictional names
-- instead of placeholder names ("Alex Example", ...).
--
-- These are SYNTHETIC demonstration records, not real people. Only rows that
-- still match the V3 seed values exactly (name, date of birth, gender, phone,
-- email, address) are changed, so a record someone has edited is left alone.
-- Patient IDs do not change, so vital signs, lab results, conditions,
-- medications, allergies, decisions and follow-ups stay linked. Phone numbers
-- stay in the fictional 555-01xx range and e-mail addresses stay on the
-- reserved example.com domain. No clinical data is added or changed.

UPDATE patients
SET full_name = 'Daniel Okafor', email = 'daniel.okafor@example.com', address = '14 Harbour Lane, Northbridge',
    updated_at = UTC_TIMESTAMP(6)
WHERE full_name = 'Alex Example' AND date_of_birth = '1958-04-12' AND gender = 'MALE'
  AND phone = '+1 555 0101' AND email = 'alex.example@example.com' AND address = '1 Sample Street, Testville';

UPDATE patients
SET full_name = 'Priya Raman', email = 'priya.raman@example.com', address = '27 Elm Court, Northbridge',
    updated_at = UTC_TIMESTAMP(6)
WHERE full_name = 'Jordan Sample' AND date_of_birth = '1972-09-30' AND gender = 'FEMALE'
  AND phone = '+1 555 0102' AND email = 'jordan.sample@example.com' AND address = '2 Demo Road, Testville';

UPDATE patients
SET full_name = 'Sam Lindqvist', email = 'sam.lindqvist@example.com', updated_at = UTC_TIMESTAMP(6)
WHERE full_name = 'Riley Demo' AND date_of_birth = '1990-01-05' AND gender = 'OTHER'
  AND phone = '+1 555 0103' AND email = 'riley.demo@example.com' AND address IS NULL;

UPDATE patients
SET full_name = 'Emma Castillo', email = 'emma.castillo@example.com', updated_at = UTC_TIMESTAMP(6)
WHERE full_name = 'Casey Fictional' AND date_of_birth = '2001-06-18' AND gender = 'FEMALE'
  AND phone IS NULL AND email = 'casey.fictional@example.com' AND address IS NULL;

-- The seeded condition notes (V6) said "Sample history (fictional)".
UPDATE patient_conditions
SET notes = 'Synthetic seed record'
WHERE notes = 'Sample history (fictional)';
