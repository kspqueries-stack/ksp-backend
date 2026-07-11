CREATE TABLE cycle_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    cycle_id        BIGINT          NOT NULL REFERENCES schedule_cycles(id),
    action          VARCHAR(50)     NOT NULL,
    description     TEXT            NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    performed_by    BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_cycle_id ON cycle_audit_log(cycle_id);
CREATE INDEX idx_audit_created_at ON cycle_audit_log(created_at DESC);
