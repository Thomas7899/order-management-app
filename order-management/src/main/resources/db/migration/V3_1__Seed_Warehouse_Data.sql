-- Seed Data for Warehouses and Inventory

-- ================ WAREHOUSES ================
INSERT INTO warehouses (id, name, location, capacity, active, created_at, updated_at) VALUES
(1, 'Hauptlager Wien', 'Wien, Österreich', 10000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Lager Graz', 'Graz, Österreich', 5000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Lager Linz', 'Linz, Österreich', 7500, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Außenlager Salzburg', 'Salzburg, Österreich', 3000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Distributionszentrum München', 'München, Deutschland', 15000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('warehouses_id_seq', (SELECT MAX(id) FROM warehouses));

-- ================ WAREHOUSE STOCKS ================
-- Verknüpfe Produkte mit Lagern und setze Bestände

-- Hauptlager Wien - hat alle Produkte
INSERT INTO warehouse_stocks (warehouse_id, product_id, quantity, min_quantity, max_quantity, created_at, updated_at)
SELECT 
    1 as warehouse_id,
    p.id as product_id,
    FLOOR(RANDOM() * 100 + 50)::INTEGER as quantity,
    10 as min_quantity,
    200 as max_quantity,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE p.active = true
ON CONFLICT (warehouse_id, product_id) DO NOTHING;

-- Lager Graz - hat ca. 70% der Produkte
INSERT INTO warehouse_stocks (warehouse_id, product_id, quantity, min_quantity, max_quantity, created_at, updated_at)
SELECT 
    2 as warehouse_id,
    p.id as product_id,
    FLOOR(RANDOM() * 50 + 20)::INTEGER as quantity,
    5 as min_quantity,
    100 as max_quantity,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE p.active = true AND RANDOM() < 0.7
ON CONFLICT (warehouse_id, product_id) DO NOTHING;

-- Lager Linz - hat ca. 60% der Produkte
INSERT INTO warehouse_stocks (warehouse_id, product_id, quantity, min_quantity, max_quantity, created_at, updated_at)
SELECT 
    3 as warehouse_id,
    p.id as product_id,
    FLOOR(RANDOM() * 40 + 15)::INTEGER as quantity,
    5 as min_quantity,
    80 as max_quantity,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE p.active = true AND RANDOM() < 0.6
ON CONFLICT (warehouse_id, product_id) DO NOTHING;

-- Außenlager Salzburg - hat ca. 40% der Produkte
INSERT INTO warehouse_stocks (warehouse_id, product_id, quantity, min_quantity, max_quantity, created_at, updated_at)
SELECT 
    4 as warehouse_id,
    p.id as product_id,
    FLOOR(RANDOM() * 30 + 10)::INTEGER as quantity,
    3 as min_quantity,
    60 as max_quantity,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE p.active = true AND RANDOM() < 0.4
ON CONFLICT (warehouse_id, product_id) DO NOTHING;

-- Distributionszentrum München - hat ca. 80% der Produkte
INSERT INTO warehouse_stocks (warehouse_id, product_id, quantity, min_quantity, max_quantity, created_at, updated_at)
SELECT 
    5 as warehouse_id,
    p.id as product_id,
    FLOOR(RANDOM() * 150 + 80)::INTEGER as quantity,
    15 as min_quantity,
    300 as max_quantity,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE p.active = true AND RANDOM() < 0.8
ON CONFLICT (warehouse_id, product_id) DO NOTHING;

-- ================ STOCK MOVEMENTS (Beispiel-Bewegungen) ================
INSERT INTO stock_movements (warehouse_id, product_id, quantity, movement_type, reference_type, reference_id, notes, created_at) 
SELECT 
    ws.warehouse_id,
    ws.product_id,
    ws.quantity,
    'INCOMING',
    'INITIAL_STOCK',
    NULL,
    'Initialer Lagerbestand',
    CURRENT_TIMESTAMP
FROM warehouse_stocks ws
ON CONFLICT DO NOTHING;
