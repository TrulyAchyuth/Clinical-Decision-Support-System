-- Illustrative clinical rules and sample patient history.
--
-- EDUCATIONAL EXAMPLES ONLY. These thresholds and actions are simplified for
-- teaching, are NOT clinically validated, and must not be used for real
-- patient care. A rule match is a prompt for clinician review, not a diagnosis.
--
-- Formatting note: each rule's condition JSON and actions JSON sit on their own
-- lines; SampleRulesTests reads this file to check every rule is valid.

INSERT INTO clinical_rules (rule_code, name, description, severity, priority, enabled, condition_json, recommended_actions, created_at, updated_at) VALUES
('SEPSIS-SCREEN', 'Possible sepsis indicators',
 'Two or more of: temperature above 38.0 C or below 36.0 C, heart rate above 90/min, respiratory rate above 20/min (simplified SIRS-style screen). Educational example; not clinically validated and not a sepsis diagnosis.',
 'HIGH', 10, TRUE,
 '{"any": [{"all": [{"any": [{"fact": "vitals.temperature", "operator": "gt", "value": 38.0}, {"fact": "vitals.temperature", "operator": "lt", "value": 36.0}]}, {"fact": "vitals.heartRate", "operator": "gt", "value": 90}]}, {"all": [{"any": [{"fact": "vitals.temperature", "operator": "gt", "value": 38.0}, {"fact": "vitals.temperature", "operator": "lt", "value": 36.0}]}, {"fact": "vitals.respiratoryRate", "operator": "gt", "value": 20}]}, {"all": [{"fact": "vitals.heartRate", "operator": "gt", "value": 90}, {"fact": "vitals.respiratoryRate", "operator": "gt", "value": 20}]}]}',
 '["Escalate to a clinician for prompt review", "Assess for a possible source of infection and check mental status", "Follow the local sepsis screening protocol"]',
 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

('SPO2-LOW', 'Low oxygen saturation',
 'Oxygen saturation below 92%. Educational example; target ranges differ between patients (for example in chronic lung disease) and must follow local guidance.',
 'HIGH', 20, TRUE,
 '{"fact": "vitals.oxygenSaturation", "operator": "lt", "value": 92}',
 '["Re-check oxygen saturation and the sensor position", "Assess breathing and escalate to a clinician promptly", "Follow local oxygen therapy guidance"]',
 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

('HR-HIGH', 'Tachycardia',
 'Heart rate above 100 beats/min at rest. Educational example; not clinically validated.',
 'MEDIUM', 30, TRUE,
 '{"fact": "vitals.heartRate", "operator": "gt", "value": 100}',
 '["Re-check the heart rate with the patient at rest", "Look for common causes such as pain, fever, dehydration or anxiety", "Consider an ECG and clinician review if it persists"]',
 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

('BP-HIGH', 'High blood pressure reading',
 'Systolic blood pressure 140 mmHg or above, or diastolic 90 mmHg or above. Educational example; a single reading does not establish hypertension.',
 'MEDIUM', 40, TRUE,
 '{"any": [{"fact": "vitals.systolicBp", "operator": "gte", "value": 140}, {"fact": "vitals.diastolicBp", "operator": "gte", "value": 90}]}',
 '["Repeat the measurement after the patient has rested for 5 minutes", "Review previous blood pressure readings and current medications", "Arrange clinician review if readings remain high"]',
 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

('GLUCOSE-HIGH', 'Elevated fasting blood glucose',
 'Fasting plasma glucose 7.0 mmol/L or above. Educational example; one result does not diagnose diabetes.',
 'MEDIUM', 50, TRUE,
 '{"fact": "lab.Fasting plasma glucose", "operator": "gte", "value": 7.0}',
 '["Confirm with a repeat fasting glucose or an HbA1c test", "Review the history for known diabetes and current medications", "Arrange clinician review"]',
 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Sample history for the fictional patients from V3 (matched by name; rows are
-- skipped if a patient or disease no longer exists).
INSERT INTO patient_conditions (patient_id, disease_id, status, onset_date, notes, created_at)
SELECT p.id, d.id, 'ACTIVE', '2015-03-01', 'Sample history (fictional)', UTC_TIMESTAMP(6)
FROM patients p JOIN diseases d ON d.icd10_code = 'I10' WHERE p.full_name = 'Alex Example';

INSERT INTO patient_conditions (patient_id, disease_id, status, onset_date, notes, created_at)
SELECT p.id, d.id, 'ACTIVE', '2019-11-15', 'Sample history (fictional)', UTC_TIMESTAMP(6)
FROM patients p JOIN diseases d ON d.icd10_code = 'E11.9' WHERE p.full_name = 'Alex Example';

INSERT INTO patient_conditions (patient_id, disease_id, status, onset_date, notes, created_at)
SELECT p.id, d.id, 'RESOLVED', '2021-01-10', 'Sample history (fictional)', UTC_TIMESTAMP(6)
FROM patients p JOIN diseases d ON d.icd10_code = 'J18.9' WHERE p.full_name = 'Jordan Sample';
