-- Patient medications and allergies.
--
-- Additive only: two nullable columns on patients and two new tables. No
-- existing row is changed or deleted. Medication and allergy records are never
-- deleted by the application; they change status (discontinued, completed,
-- resolved) so the history is kept. They are deleted only together with
-- their patient (ON DELETE CASCADE, like every other clinical record).

-- "No known allergies" must be recorded explicitly. When these columns are
-- NULL and the patient has no active allergy records, the allergy history is
-- reported as NOT ASSESSED, never as "no allergies".
ALTER TABLE patients
    ADD COLUMN no_known_allergies_at DATETIME(6) NULL,
    ADD COLUMN no_known_allergies_by VARCHAR(50)  NULL;

-- One row per medication course. The drug comes from the knowledge base.
-- safety_warnings holds the safety-check warnings the clinician acknowledged
-- when the medication was added (JSON array of strings, empty if none).
CREATE TABLE patient_medications (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id             BIGINT        NOT NULL,
    drug_id                BIGINT        NOT NULL,
    dose                   DECIMAL(10,2) NOT NULL,
    dose_unit              VARCHAR(20)   NOT NULL,
    route                  VARCHAR(20)   NOT NULL,
    frequency              VARCHAR(100)  NOT NULL,
    start_date             DATE          NOT NULL,
    end_date               DATE          NULL,
    status                 VARCHAR(15)   NOT NULL,
    notes                  VARCHAR(255)  NULL,
    status_reason          VARCHAR(255)  NULL,
    status_changed_at      DATETIME(6)   NULL,
    status_changed_by      VARCHAR(50)   NULL,
    safety_warnings        JSON          NOT NULL,
    safety_override_reason VARCHAR(500)  NULL,
    recorded_by            VARCHAR(50)   NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_patient_medications_patient_status (patient_id, status),
    CONSTRAINT fk_patient_medications_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_medications_drug FOREIGN KEY (drug_id) REFERENCES drugs (id),
    CONSTRAINT chk_patient_medications_status CHECK (status IN ('ACTIVE', 'DISCONTINUED', 'COMPLETED')),
    CONSTRAINT chk_patient_medications_route CHECK (route IN ('ORAL', 'INTRAVENOUS', 'INTRAMUSCULAR', 'SUBCUTANEOUS',
        'TOPICAL', 'INHALED', 'SUBLINGUAL', 'RECTAL', 'OTHER')),
    CONSTRAINT chk_patient_medications_dose CHECK (dose > 0),
    CONSTRAINT chk_patient_medications_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

-- A DRUG allergy points to a knowledge-base drug (drug_id) and matches every
-- strength of a drug with that name. A DRUG_CATEGORY allergy stores a
-- knowledge-base drug category exactly as written there. FOOD, ENVIRONMENTAL
-- and OTHER allergies are free text and are never matched to drugs
-- automatically.
CREATE TABLE patient_allergies (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id        BIGINT       NOT NULL,
    drug_id           BIGINT       NULL,
    allergen          VARCHAR(150) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    reaction          VARCHAR(255) NULL,
    severity          VARCHAR(10)  NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    recorded_date     DATE         NOT NULL,
    notes             VARCHAR(255) NULL,
    resolution_reason VARCHAR(255) NULL,
    resolved_at       DATETIME(6)  NULL,
    resolved_by       VARCHAR(50)  NULL,
    recorded_by       VARCHAR(50)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_patient_allergies_patient_status (patient_id, status),
    CONSTRAINT fk_patient_allergies_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_allergies_drug FOREIGN KEY (drug_id) REFERENCES drugs (id),
    CONSTRAINT chk_patient_allergies_category CHECK (category IN ('DRUG', 'DRUG_CATEGORY', 'FOOD', 'ENVIRONMENTAL', 'OTHER')),
    CONSTRAINT chk_patient_allergies_severity CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE', 'UNKNOWN')),
    CONSTRAINT chk_patient_allergies_status CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT chk_patient_allergies_drug_link CHECK ((category = 'DRUG') = (drug_id IS NOT NULL))
);
