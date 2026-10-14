-- Patient follow-ups (scheduled reviews).
--
-- Additive only: one new table. scheduled_at is stored in UTC like every
-- other timestamp. Whether a follow-up is upcoming or overdue is calculated
-- when it is read, never stored, and only for SCHEDULED rows: a completed or
-- cancelled follow-up is never overdue. Follow-ups are not deleted; they are
-- completed or cancelled (or deleted together with their patient).
CREATE TABLE follow_ups (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id        BIGINT       NOT NULL,
    scheduled_at      DATETIME(6)  NOT NULL,
    reason            VARCHAR(200) NOT NULL,
    notes             VARCHAR(500) NULL,
    status            VARCHAR(10)  NOT NULL,
    reschedule_count  INT          NOT NULL,
    outcome_notes     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    status_changed_by VARCHAR(50)  NULL,
    created_by        VARCHAR(50)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_follow_ups_status_time (status, scheduled_at),
    INDEX idx_follow_ups_patient_time (patient_id, scheduled_at),
    CONSTRAINT fk_follow_ups_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_follow_ups_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_follow_ups_reschedules CHECK (reschedule_count >= 0)
);
