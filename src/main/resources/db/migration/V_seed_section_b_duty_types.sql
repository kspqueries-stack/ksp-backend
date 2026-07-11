-- Clear existing Section B duty types to avoid duplicates
DELETE FROM duty_types WHERE section = 'B';

-- Section B top-level categories (no sub-categories in Section B per reference)
INSERT INTO duty_types (name, section, sort_order, parent_id) VALUES
('OFFICE WRITER (DCP, ACP, RPI) / COMPUTER OPERATOR', 'B', 1, NULL),
('POLICE CANTEEN', 'B', 2, NULL),
('POLICE LANE IN-CHARGE', 'B', 3, NULL),
('CAR STORE', 'B', 4, NULL),
('CAR BUILDING MAINTENANCE', 'B', 5, NULL),
('AROGYA BHAGYA COORDINATOR', 'B', 6, NULL),
('COP COMPUTER WING', 'B', 7, NULL),
('STATE LEVEL SPORTS', 'B', 8, NULL),
('BAND TEAM', 'B', 9, NULL),
('QRT TEAM', 'B', 10, NULL),
('CPT TEAM', 'B', 11, NULL);
