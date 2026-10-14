-- Patient clinical records: vital signs, lab results and conditions (history).
--
-- Every record belongs to a patient and is deleted with the patient
-- (ON DELETE CASCADE). Lab results and conditions point to the knowledge base;
-- a lab test or disease that is still referenced cannot be deleted.

-- One row per set of vital signs taken at the same time. Individual values may
-- be missing, but at least one must be present. Units are fixed:
-- blood pressure mmHg, temperature degrees Celsius, heart rate beats/min,
-- respiratory rate breaths/min, oxygen saturation %.
CREATE TABLE vital_signs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id        BIGINT       NOT NULL,
    recorded_at       DATETIME(6)  NOT NULL,
    systolic_bp       INT          NULL,
    diastolic_bp      INT          NULL,
    temperature       DECIMAL(4,1) NULL,
    heart_rate        INT          NULL,
    respiratory_rate  INT          NULL,
    oxygen_saturation INT          NULL,
    notes             VARCHAR(255) NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_vital_signs_patient_time (patient_id, recorded_at),
    CONSTRAINT fk_vital_signs_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_vital_signs_not_empty CHECK (
        systolic_bp IS NOT NULL OR diastolic_bp IS NOT NULL OR temperature IS NOT NULL
        OR heart_rate IS NOT NULL OR respiratory_rate IS NOT NULL OR oxygen_saturation IS NOT NULL)
);

-- The unit is copied from the lab test so each result is self-describing.
CREATE TABLE lab_results (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id  BIGINT        NOT NULL,
    lab_test_id BIGINT        NOT NULL,
    value       DECIMAL(10,2) NOT NULL,
    unit        VARCHAR(30)   NOT NULL,
    measured_at DATETIME(6)   NOT NULL,
    notes       VARCHAR(255)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_lab_results_patient_time (patient_id, measured_at),
    CONSTRAINT fk_lab_results_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_results_lab_test FOREIGN KEY (lab_test_id) REFERENCES lab_tests (id)
);

-- Patient history: diagnoses from the knowledge base, at most once per patient.
CREATE TABLE patient_conditions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id BIGINT       NOT NULL,
    disease_id BIGINT       NOT NULL,
    status     VARCHAR(10)  NOT NULL,
    onset_date DATE         NULL,
    notes      VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_patient_conditions_patient_disease UNIQUE (patient_id, disease_id),
    CONSTRAINT fk_patient_conditions_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_conditions_disease FOREIGN KEY (disease_id) REFERENCES diseases (id),
    CONSTRAINT chk_patient_conditions_status CHECK (status IN ('ACTIVE', 'RESOLVED'))
);
