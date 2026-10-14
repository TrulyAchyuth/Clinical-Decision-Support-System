-- Illustrative sample data so the application has something to show.
--
-- * Patients are fictional (example.com e-mail addresses, 555-01xx phone numbers).
-- * Dosages and laboratory reference ranges are EDUCATIONAL EXAMPLES ONLY.
--   They are not prescribing guidance and must be verified against local
--   formularies and laboratory reference ranges before any real use.

INSERT INTO patients (full_name, date_of_birth, gender, phone, email, address, created_at, updated_at) VALUES
    ('Alex Example',   '1958-04-12', 'MALE',   '+1 555 0101', 'alex.example@example.com',   '1 Sample Street, Testville', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Jordan Sample',  '1972-09-30', 'FEMALE', '+1 555 0102', 'jordan.sample@example.com',  '2 Demo Road, Testville',     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Riley Demo',     '1990-01-05', 'OTHER',  '+1 555 0103', 'riley.demo@example.com',     NULL,                         UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Casey Fictional','2001-06-18', 'FEMALE', NULL,          'casey.fictional@example.com', NULL,                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO drugs (name, dosage, category, notes) VALUES
    ('Amoxicillin', '500 mg', 'Antibiotic (penicillin)',   'Educational example dosage - not prescribing guidance.'),
    ('Lisinopril',  '10 mg',  'ACE inhibitor',             'Educational example dosage - not prescribing guidance.'),
    ('Metformin',   '500 mg', 'Antidiabetic (biguanide)',  'Educational example dosage - not prescribing guidance.'),
    ('Warfarin',    '5 mg',   'Anticoagulant',             'Educational example dosage - not prescribing guidance.'),
    ('Ibuprofen',   '400 mg', 'NSAID',                     'Educational example dosage - not prescribing guidance.');

INSERT INTO diseases (name, icd10_code) VALUES
    ('Type 2 diabetes mellitus without complications', 'E11.9'),
    ('Essential (primary) hypertension',               'I10'),
    ('Asthma, unspecified',                            'J45.9'),
    ('Chronic kidney disease, stage 3',                'N18.3'),
    ('Pneumonia, unspecified organism',                'J18.9');

INSERT INTO lab_tests (name, reference_range_low, reference_range_high, unit, notes) VALUES
    ('Potassium',             3.50,  5.10, 'mmol/L', 'Educational example range - verify against your local laboratory.'),
    ('Fasting plasma glucose', 3.90,  5.50, 'mmol/L', 'Educational example range - verify against your local laboratory.'),
    ('HbA1c',                 4.00,  5.60, '%',      'Educational example range - verify against your local laboratory.'),
    ('Creatinine',            0.60,  1.20, 'mg/dL',  'Educational example range - verify against your local laboratory.'),
    ('Haemoglobin',          12.00, 17.50, 'g/dL',   'Educational example range - verify against your local laboratory.');
