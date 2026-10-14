-- Drug-drug interactions used by the medication safety check.
--
-- ILLUSTRATIVE SAMPLE DATA ONLY. No verified, licensed interaction source is
-- available to this project. The rows below are a tiny teaching sample: they
-- are NOT a drug-interaction database, are NOT clinically reviewed, and must
-- not be used for real patient care. The absence of a row never means two
-- drugs are safe together. Every row says so in data_classification,
-- review_status and source, and the application shows that to users.
--
-- Interactions are keyed by drug NAME (not by knowledge-base row), so they
-- apply to every strength of a drug. drug_name_a sorts before drug_name_b, so
-- each pair is stored once.
CREATE TABLE drug_interactions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    drug_name_a         VARCHAR(150)  NOT NULL,
    drug_name_b         VARCHAR(150)  NOT NULL,
    severity            VARCHAR(10)   NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    source              VARCHAR(255)  NOT NULL,
    data_classification VARCHAR(30)   NOT NULL,
    review_status       VARCHAR(30)   NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_drug_interactions_pair UNIQUE (drug_name_a, drug_name_b),
    CONSTRAINT chk_drug_interactions_order CHECK (drug_name_a < drug_name_b),
    CONSTRAINT chk_drug_interactions_severity CHECK (severity IN ('MINOR', 'MODERATE', 'MAJOR')),
    CONSTRAINT chk_drug_interactions_classification CHECK (data_classification IN ('ILLUSTRATIVE_SAMPLE', 'VERIFIED')),
    CONSTRAINT chk_drug_interactions_review CHECK (review_status IN ('NOT_CLINICALLY_REVIEWED', 'CLINICALLY_REVIEWED'))
);

-- Formatting note: one row per line; DrugInteractionSampleDataTests reads this file.
INSERT INTO drug_interactions (drug_name_a, drug_name_b, severity, description, source, data_classification, review_status, created_at) VALUES
('Ibuprofen', 'Warfarin', 'MAJOR', 'ILLUSTRATIVE SAMPLE: an anticoagulant combined with an NSAID is used here to demonstrate a bleeding-risk interaction warning. Not authoritative clinical guidance.', 'Illustrative teaching sample written for this prototype; not from a licensed or verified interaction source', 'ILLUSTRATIVE_SAMPLE', 'NOT_CLINICALLY_REVIEWED', UTC_TIMESTAMP(6)),
('Ibuprofen', 'Lisinopril', 'MODERATE', 'ILLUSTRATIVE SAMPLE: an ACE inhibitor combined with an NSAID is used here to demonstrate a blood-pressure and kidney-function interaction warning. Not authoritative clinical guidance.', 'Illustrative teaching sample written for this prototype; not from a licensed or verified interaction source', 'ILLUSTRATIVE_SAMPLE', 'NOT_CLINICALLY_REVIEWED', UTC_TIMESTAMP(6));
