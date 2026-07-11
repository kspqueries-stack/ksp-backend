-- Add duty_type_id FK column to personnel
ALTER TABLE personnel ADD COLUMN IF NOT EXISTS duty_type_id BIGINT REFERENCES duty_types(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_personnel_duty_type_id ON personnel(duty_type_id);

-- Migrate existing free-text duty_type values to duty_type_id
-- First try exact match (case-insensitive)
UPDATE personnel p
SET duty_type_id = dt.id
FROM duty_types dt
WHERE UPPER(TRIM(p.duty_type)) = UPPER(TRIM(dt.name))
  AND p.duty_type IS NOT NULL
  AND p.duty_type_id IS NULL;

-- Then try partial match for sub-duty types
UPDATE personnel p
SET duty_type_id = dt.id
FROM duty_types dt
WHERE dt.parent_id IS NOT NULL
  AND UPPER(TRIM(p.duty_type)) LIKE '%' || UPPER(TRIM(dt.name)) || '%'
  AND p.duty_type_id IS NULL;
