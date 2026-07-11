-- ============================================================
-- Platoon Cycle Management Tables
-- Creates schedule_cycles, cycle_platoon_sections, and
-- cycle_duty_assignments tables for rotation schedule management
-- ============================================================

-- schedule_cycles: defines rotation schedule periods
CREATE TABLE schedule_cycles (
    id              BIGSERIAL       PRIMARY KEY,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    rotation_days   INT             NOT NULL CHECK (rotation_days > 0),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'COMPLETED', 'DELETED')),
    created_by      BIGINT          REFERENCES users(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- cycle_platoon_sections: maps platoons to sections within a cycle
CREATE TABLE cycle_platoon_sections (
    id              BIGSERIAL       PRIMARY KEY,
    cycle_id        BIGINT          NOT NULL REFERENCES schedule_cycles(id),
    platoon_id      BIGINT          NOT NULL REFERENCES platoons(id),
    section_id      BIGINT          NOT NULL REFERENCES sections(id),
    CONSTRAINT uq_cycle_platoon_section UNIQUE (cycle_id, platoon_id, section_id)
);

-- cycle_duty_assignments: individual duty assignments generated from cycle configuration
CREATE TABLE cycle_duty_assignments (
    id              BIGSERIAL       PRIMARY KEY,
    cycle_id        BIGINT          NOT NULL REFERENCES schedule_cycles(id),
    date            DATE            NOT NULL,
    platoon_id      BIGINT          NOT NULL REFERENCES platoons(id),
    section_id      BIGINT          NOT NULL REFERENCES sections(id),
    person_id       BIGINT          NOT NULL REFERENCES personnel(id),
    shift_type      VARCHAR(10)     NOT NULL DEFAULT 'DAY',
    is_override     BOOLEAN         NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cycle_duty_cycle_date ON cycle_duty_assignments(cycle_id, date);
CREATE INDEX idx_cycle_duty_person ON cycle_duty_assignments(person_id, date);
