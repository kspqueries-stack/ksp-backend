-- ============================================================================
-- Migration: Add geolocation columns to duty_types table
-- Purpose:   Adds latitude, longitude, and radius_meters columns to support
--            geographic visualization of duty type locations on an interactive map.
-- Date:      YYYY-MM-DD (replace with actual migration date)
-- Author:    KSP WorkBoard Admin
-- ============================================================================
-- ROLLBACK SECTION (execute manually to reverse this migration):
--   ALTER TABLE duty_types DROP CONSTRAINT IF EXISTS chk_lat_lng_paired;
--   ALTER TABLE duty_types DROP CONSTRAINT IF EXISTS chk_lat_range;
--   ALTER TABLE duty_types DROP CONSTRAINT IF EXISTS chk_lng_range;
--   ALTER TABLE duty_types DROP CONSTRAINT IF EXISTS chk_radius_range;
--   ALTER TABLE duty_types DROP COLUMN IF EXISTS latitude;
--   ALTER TABLE duty_types DROP COLUMN IF EXISTS longitude;
--   ALTER TABLE duty_types DROP COLUMN IF EXISTS radius_meters;
-- ============================================================================

BEGIN;

-- Add latitude column (DOUBLE PRECISION, nullable)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'duty_types' AND column_name = 'latitude'
    ) THEN
        ALTER TABLE duty_types ADD COLUMN latitude DOUBLE PRECISION;
    END IF;
END $$;

-- Add longitude column (DOUBLE PRECISION, nullable)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'duty_types' AND column_name = 'longitude'
    ) THEN
        ALTER TABLE duty_types ADD COLUMN longitude DOUBLE PRECISION;
    END IF;
END $$;

-- Add radius_meters column (INTEGER, nullable, default 500)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'duty_types' AND column_name = 'radius_meters'
    ) THEN
        ALTER TABLE duty_types ADD COLUMN radius_meters INTEGER DEFAULT 500;
    END IF;
END $$;

-- Add CHECK constraint: latitude must be between -90 and 90
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.constraint_column_usage
        WHERE table_name = 'duty_types' AND constraint_name = 'chk_lat_range'
    ) THEN
        ALTER TABLE duty_types ADD CONSTRAINT chk_lat_range
            CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90));
    END IF;
END $$;

-- Add CHECK constraint: longitude must be between -180 and 180
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.constraint_column_usage
        WHERE table_name = 'duty_types' AND constraint_name = 'chk_lng_range'
    ) THEN
        ALTER TABLE duty_types ADD CONSTRAINT chk_lng_range
            CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180));
    END IF;
END $$;

-- Add CHECK constraint: radius_meters must be between 1 and 100000
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.constraint_column_usage
        WHERE table_name = 'duty_types' AND constraint_name = 'chk_radius_range'
    ) THEN
        ALTER TABLE duty_types ADD CONSTRAINT chk_radius_range
            CHECK (radius_meters IS NULL OR (radius_meters >= 1 AND radius_meters <= 100000));
    END IF;
END $$;

-- Add CHECK constraint: latitude and longitude must both be NULL or both NOT NULL
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'duty_types' AND constraint_name = 'chk_lat_lng_paired'
    ) THEN
        ALTER TABLE duty_types ADD CONSTRAINT chk_lat_lng_paired
            CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL));
    END IF;
END $$;

COMMIT;
