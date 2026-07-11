-- Clear existing Section A duty types to avoid duplicates
DELETE FROM duty_types WHERE section = 'A';

-- Section A top-level categories
INSERT INTO duty_types (name, section, sort_order, parent_id) VALUES
('DCP, ACP, RPI', 'A', 1, NULL),
('RSI(DUTY OFFICER), ARSI (ADO)', 'A', 2, NULL),
('GARDEN DUTY', 'A', 3, NULL),
('CHAMBER SENTRY', 'A', 4, NULL),
('ARMOURY', 'A', 5, NULL),
('DOG SQUAD', 'A', 6, NULL),
('ASC TEAM', 'A', 7, NULL),
('GUNMAN', 'A', 8, NULL),
('OOD', 'A', 9, NULL),
('BAJPE AIRPORT LIAISON DUTY', 'A', 10, NULL),
('PHOTOGRAPHER', 'A', 11, NULL),
('DIST CONSUMER DISPUTES OFFICE DUTY', 'A', 12, NULL),
('BUGLER', 'A', 13, NULL);

-- Sub-duty types under GARDEN DUTY (parent = 'GARDEN DUTY')
INSERT INTO duty_types (name, section, sort_order, parent_id) VALUES
('CP BUNGLOW GARDEN DUTY', 'A', 1, (SELECT id FROM duty_types WHERE name = 'GARDEN DUTY' AND section = 'A' AND parent_id IS NULL)),
('COP GARDENER', 'A', 2, (SELECT id FROM duty_types WHERE name = 'GARDEN DUTY' AND section = 'A' AND parent_id IS NULL));

-- Sub-duty types under CHAMBER SENTRY
INSERT INTO duty_types (name, section, sort_order, parent_id) VALUES
('COMMISSIONER OFFICE', 'A', 1, (SELECT id FROM duty_types WHERE name = 'CHAMBER SENTRY' AND section = 'A' AND parent_id IS NULL)),
('DCP CAR OFFICE', 'A', 2, (SELECT id FROM duty_types WHERE name = 'CHAMBER SENTRY' AND section = 'A' AND parent_id IS NULL)),
('DCP LAW & ORDER OFFICE', 'A', 3, (SELECT id FROM duty_types WHERE name = 'CHAMBER SENTRY' AND section = 'A' AND parent_id IS NULL)),
('ACP CAR OFFICE', 'A', 4, (SELECT id FROM duty_types WHERE name = 'CHAMBER SENTRY' AND section = 'A' AND parent_id IS NULL)),
('DCP CRIME & TRAFFIC OFFICE', 'A', 5, (SELECT id FROM duty_types WHERE name = 'CHAMBER SENTRY' AND section = 'A' AND parent_id IS NULL));

-- Sub-duty types under OOD
INSERT INTO duty_types (name, section, sort_order, parent_id) VALUES
('BDDS WESTERN RANGE MANGALURU', 'A', 1, (SELECT id FROM duty_types WHERE name = 'OOD' AND section = 'A' AND parent_id IS NULL)),
('CCT KUDLU', 'A', 2, (SELECT id FROM duty_types WHERE name = 'OOD' AND section = 'A' AND parent_id IS NULL)),
('FPB MANGALURU', 'A', 3, (SELECT id FROM duty_types WHERE name = 'OOD' AND section = 'A' AND parent_id IS NULL)),
('CONTROL ROOM MGC', 'A', 4, (SELECT id FROM duty_types WHERE name = 'OOD' AND section = 'A' AND parent_id IS NULL)),
('DCP CRIME & TRAFFIC OFFICE', 'A', 5, (SELECT id FROM duty_types WHERE name = 'OOD' AND section = 'A' AND parent_id IS NULL));
