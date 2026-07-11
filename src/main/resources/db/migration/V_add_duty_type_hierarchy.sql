-- Add parent_id column for self-referencing hierarchy
ALTER TABLE duty_types ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES duty_types(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_duty_types_parent_id ON duty_types(parent_id);
