-- Clinical rules (JSON conditions) and the history of decisions made with them.

-- condition_json holds the rule's condition tree; recommended_actions is a JSON
-- array of strings. Both are validated by the application before saving.
-- priority: 1 = most important; results are listed in priority order.
CREATE TABLE clinical_rules (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    rule_code           VARCHAR(50)   NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    description         VARCHAR(1000) NULL,
    severity            VARCHAR(10)   NOT NULL,
    priority            INT           NOT NULL,
    enabled             BOOLEAN       NOT NULL,
    condition_json      JSON          NOT NULL,
    recommended_actions JSON          NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_clinical_rules_rule_code UNIQUE (rule_code),
    CONSTRAINT chk_clinical_rules_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_clinical_rules_priority CHECK (priority BETWEEN 1 AND 1000)
);

-- One row each time a decision is requested for a patient.
CREATE TABLE clinical_decisions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id      BIGINT       NOT NULL,
    requested_at    DATETIME(6)  NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    rules_evaluated INT          NOT NULL,
    summary         VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_clinical_decisions_patient_time (patient_id, requested_at),
    CONSTRAINT fk_clinical_decisions_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_clinical_decisions_outcome CHECK (outcome IN ('RULES_MATCHED', 'NO_RULES_MATCHED'))
);

-- Rules that matched, could not be evaluated (missing data), or were invalid
-- in a decision. Rule code, name, severity and actions are copied so the
-- history stays readable if the rule is later edited or deleted.
CREATE TABLE decision_rule_results (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    decision_id         BIGINT       NOT NULL,
    rule_id             BIGINT       NULL,
    rule_code           VARCHAR(50)  NOT NULL,
    rule_name           VARCHAR(150) NOT NULL,
    severity            VARCHAR(10)  NOT NULL,
    priority            INT          NOT NULL,
    result              VARCHAR(20)  NOT NULL,
    reasons             JSON         NOT NULL,
    recommended_actions JSON         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_decision_rule_results_decision FOREIGN KEY (decision_id) REFERENCES clinical_decisions (id) ON DELETE CASCADE,
    CONSTRAINT fk_decision_rule_results_rule FOREIGN KEY (rule_id) REFERENCES clinical_rules (id) ON DELETE SET NULL,
    CONSTRAINT chk_decision_rule_results_result CHECK (result IN ('MATCHED', 'INSUFFICIENT_DATA', 'INVALID_RULE'))
);
