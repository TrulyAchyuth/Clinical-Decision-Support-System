-- Patients and the knowledge base (drugs, diseases, lab tests).
--
-- Duplicate rules are enforced here as unique constraints and also checked by
-- the services, which return a clear 409 Conflict message.

CREATE TABLE patients (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(150) NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        VARCHAR(10)  NOT NULL,
    phone         VARCHAR(30)  NULL,
    email         VARCHAR(254) NULL,
    address       VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_patients_full_name (full_name),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN'))
);

-- A drug is identified by its name and dosage, so "Amoxicillin 250 mg" and
-- "Amoxicillin 500 mg" can both exist.
CREATE TABLE drugs (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(150) NOT NULL,
    dosage   VARCHAR(100) NOT NULL,
    category VARCHAR(100) NOT NULL,
    notes    VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_drugs_name_dosage UNIQUE (name, dosage)
);

CREATE TABLE diseases (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(200) NOT NULL,
    icd10_code VARCHAR(10)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_diseases_icd10_code UNIQUE (icd10_code)
);

CREATE TABLE lab_tests (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(150)  NOT NULL,
    reference_range_low  DECIMAL(10,2) NOT NULL,
    reference_range_high DECIMAL(10,2) NOT NULL,
    unit                 VARCHAR(30)   NOT NULL,
    notes                VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_tests_name UNIQUE (name),
    CONSTRAINT chk_lab_tests_range CHECK (reference_range_low <= reference_range_high)
);
