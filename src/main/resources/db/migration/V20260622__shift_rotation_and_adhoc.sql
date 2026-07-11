-- Shift groups table (stores A/B/C group division per cycle)
CREATE TABLE IF NOT EXISTS shift_groups (
    id BIGSERIAL PRIMARY KEY,
    cycle_id BIGINT NOT NULL REFERENCES schedule_cycles(id) ON DELETE CASCADE,
    section_id BIGINT NOT NULL,
    platoon_id BIGINT NOT NULL,
    group_name VARCHAR(1) NOT NULL CHECK (group_name IN ('A', 'B', 'C')),
    personnel_ids JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_shift_groups_cycle ON shift_groups(cycle_id);

-- Add new columns to cycle_duty_assignments
ALTER TABLE cycle_duty_assignments ADD COLUMN IF NOT EXISTS group_index VARCHAR(1);
ALTER TABLE cycle_duty_assignments ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE cycle_duty_assignments ADD COLUMN IF NOT EXISTS adhoc_duty_id BIGINT;
ALTER TABLE cycle_duty_assignments ADD COLUMN IF NOT EXISTS duty_name VARCHAR(100);

-- Update existing rows to have status = 'ACTIVE'
UPDATE cycle_duty_assignments SET status = 'ACTIVE' WHERE status IS NULL;

-- Adhoc duties table
CREATE TABLE IF NOT EXISTS adhoc_duties (
    id BIGSERIAL PRIMARY KEY,
    duty_name VARCHAR(200) NOT NULL,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    required_count INT NOT NULL CHECK (required_count > 0),
    actual_count INT DEFAULT 0,
    pick_from VARCHAR(50) DEFAULT 'ALL_PLATOONS',
    platoon_ids JSONB,
    location VARCHAR(300),
    min_strength_check BOOLEAN DEFAULT true,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_adhoc_duties_date ON adhoc_duties(date);
CREATE INDEX idx_adhoc_duties_status ON adhoc_duties(status);

-- Adhoc duty assignments table
CREATE TABLE IF NOT EXISTS adhoc_duty_assignments (
    id BIGSERIAL PRIMARY KEY,
    adhoc_duty_id BIGINT NOT NULL REFERENCES adhoc_duties(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL,
    original_assignment_id BIGINT REFERENCES cycle_duty_assignments(id),
    original_shift VARCHAR(20),
    original_duty_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ASSIGNED' CHECK (status IN ('ASSIGNED', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_adhoc_assignments_duty ON adhoc_duty_assignments(adhoc_duty_id);
CREATE INDEX idx_adhoc_assignments_person ON adhoc_duty_assignments(person_id);
