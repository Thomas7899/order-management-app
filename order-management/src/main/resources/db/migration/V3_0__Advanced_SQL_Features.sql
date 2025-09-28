-- Advanced SQL Features für Order Management App
-- Performance-Optimierung und Index-Strategien

-- ================ DATABASE INDEXES ================

-- Index für häufige Kategorie-Suchen
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category) WHERE active = true;

-- Composite Index für Preis-Kategorie-Suchen
CREATE INDEX IF NOT EXISTS idx_products_category_price ON products(category, price) WHERE active = true;

-- Index für Zeitbereich-Abfragen
CREATE INDEX IF NOT EXISTS idx_products_created_at ON products(created_at);

-- Partial Index nur für aktive Produkte
CREATE INDEX IF NOT EXISTS idx_products_active_stock ON products(stock_quantity) WHERE active = true;

-- Full-Text Search Index (für PostgreSQL)
CREATE INDEX IF NOT EXISTS idx_products_search ON products USING gin(to_tsvector('german', name || ' ' || COALESCE(description, '')));

-- ================ DATABASE VIEWS ================

-- View für Produkt-Analytics
CREATE OR REPLACE VIEW product_analytics AS
SELECT 
    p.id,
    p.name,
    p.category,
    p.price,
    p.stock_quantity,
    p.created_at,
    -- Window Functions
    ROW_NUMBER() OVER (PARTITION BY p.category ORDER BY p.price DESC) as category_price_rank,
    ROW_NUMBER() OVER (ORDER BY p.created_at DESC) as newest_rank,
    -- Aggregationen
    AVG(p.price) OVER (PARTITION BY p.category) as category_avg_price,
    COUNT(*) OVER (PARTITION BY p.category) as category_product_count,
    -- Inventory Value
    (p.price * p.stock_quantity) as inventory_value
FROM products p
WHERE p.active = true;

-- View für Kategorie-Statistiken
CREATE OR REPLACE VIEW category_statistics AS
SELECT 
    category,
    COUNT(*) as product_count,
    AVG(price) as avg_price,
    MIN(price) as min_price,
    MAX(price) as max_price,
    SUM(stock_quantity) as total_stock,
    SUM(price * stock_quantity) as total_inventory_value,
    AVG(price * stock_quantity) as avg_product_value
FROM products 
WHERE active = true
GROUP BY category;

-- View für Low-Stock Monitoring
CREATE OR REPLACE VIEW low_stock_products AS
SELECT 
    p.*,
    CASE 
        WHEN p.stock_quantity = 0 THEN 'OUT_OF_STOCK'
        WHEN p.stock_quantity <= 5 THEN 'LOW_STOCK'
        WHEN p.stock_quantity <= 10 THEN 'WARNING'
        ELSE 'NORMAL'
    END as stock_status
FROM products p
WHERE p.active = true
  AND p.stock_quantity <= 10
ORDER BY p.stock_quantity ASC, p.category;

-- ================ STORED PROCEDURES ================

-- Stored Procedure für Inventory Update
CREATE OR REPLACE FUNCTION update_product_stock(
    p_product_id BIGINT,
    p_quantity_change INTEGER,
    p_operation VARCHAR(10) -- 'ADD' or 'SUBTRACT'
)
RETURNS TABLE(
    product_id BIGINT,
    old_stock INTEGER,
    new_stock INTEGER,
    operation_success BOOLEAN
) AS $$
DECLARE
    current_stock INTEGER;
    new_stock_value INTEGER;
BEGIN
    -- Get current stock
    SELECT stock_quantity INTO current_stock 
    FROM products 
    WHERE id = p_product_id AND active = true;
    
    IF current_stock IS NULL THEN
        RETURN QUERY SELECT p_product_id, 0, 0, false;
        RETURN;
    END IF;
    
    -- Calculate new stock
    IF p_operation = 'ADD' THEN
        new_stock_value := current_stock + p_quantity_change;
    ELSIF p_operation = 'SUBTRACT' THEN
        new_stock_value := current_stock - p_quantity_change;
        -- Prevent negative stock
        IF new_stock_value < 0 THEN
            new_stock_value := 0;
        END IF;
    ELSE
        RETURN QUERY SELECT p_product_id, current_stock, current_stock, false;
        RETURN;
    END IF;
    
    -- Update stock
    UPDATE products 
    SET stock_quantity = new_stock_value, updated_at = CURRENT_TIMESTAMP
    WHERE id = p_product_id;
    
    RETURN QUERY SELECT p_product_id, current_stock, new_stock_value, true;
END;
$$ LANGUAGE plpgsql;

-- Function für Price Analysis
CREATE OR REPLACE FUNCTION get_price_percentiles()
RETURNS TABLE(
    percentile_25 DECIMAL(10,2),
    percentile_50 DECIMAL(10,2),
    percentile_75 DECIMAL(10,2),
    percentile_90 DECIMAL(10,2),
    percentile_95 DECIMAL(10,2)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY price)::DECIMAL(10,2) as percentile_25,
        PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY price)::DECIMAL(10,2) as percentile_50,
        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY price)::DECIMAL(10,2) as percentile_75,
        PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY price)::DECIMAL(10,2) as percentile_90,
        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY price)::DECIMAL(10,2) as percentile_95
    FROM products 
    WHERE active = true;
END;
$$ LANGUAGE plpgsql;

-- ================ TRIGGERS ================

-- Trigger für Audit Log
CREATE OR REPLACE FUNCTION product_audit_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- Log significant price changes
        IF ABS(NEW.price - OLD.price) > (OLD.price * 0.1) THEN
            INSERT INTO product_audit_log (
                product_id, 
                operation, 
                old_price, 
                new_price, 
                change_percentage,
                changed_at
            ) VALUES (
                NEW.id,
                'PRICE_CHANGE',
                OLD.price,
                NEW.price,
                ((NEW.price - OLD.price) / OLD.price * 100),
                CURRENT_TIMESTAMP
            );
        END IF;
        
        -- Update timestamp
        NEW.updated_at = CURRENT_TIMESTAMP;
        RETURN NEW;
    END IF;
    
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Create audit log table if not exists
CREATE TABLE IF NOT EXISTS product_audit_log (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    old_price DECIMAL(10,2),
    new_price DECIMAL(10,2),
    change_percentage DECIMAL(5,2),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Attach trigger to products table
DROP TRIGGER IF EXISTS product_audit_trigger ON products;
CREATE TRIGGER product_audit_trigger
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION product_audit_trigger();

-- ================ PERFORMANCE MONITORING ================

-- View für Query Performance Monitoring
CREATE OR REPLACE VIEW query_performance_stats AS
SELECT 
    schemaname,
    tablename,
    attname as column_name,
    n_distinct,
    most_common_vals,
    most_common_freqs,
    correlation
FROM pg_stats 
WHERE schemaname = 'public' 
  AND tablename = 'products';

-- ================ SAMPLE DATA GENERATION ================

-- Function zur Generierung von Testdaten
CREATE OR REPLACE FUNCTION generate_sample_products(num_products INTEGER)
RETURNS INTEGER AS $$
DECLARE
    i INTEGER;
    categories TEXT[] := ARRAY['ELEKTRONIK', 'MOEBEL', 'BELEUCHTUNG', 'OUTDOOR', 'SPORT', 'BUECHER'];
    category_item TEXT;
    price_val DECIMAL(10,2);
    stock_val INTEGER;
BEGIN
    FOR i IN 1..num_products LOOP
        category_item := categories[1 + (random() * (array_length(categories, 1) - 1))::INTEGER];
        price_val := (random() * 1000 + 10)::DECIMAL(10,2);
        stock_val := (random() * 100 + 1)::INTEGER;
        
        INSERT INTO products (
            name, 
            description, 
            price, 
            category, 
            stock_quantity, 
            active, 
            created_at, 
            updated_at
        ) VALUES (
            'Sample Product ' || i,
            'Generated test product for category ' || category_item,
            price_val,
            category_item,
            stock_val,
            true,
            CURRENT_TIMESTAMP - (random() * interval '365 days'),
            CURRENT_TIMESTAMP
        );
    END LOOP;
    
    RETURN num_products;
END;
$$ LANGUAGE plpgsql;

-- ================ MAINTENANCE QUERIES ================

-- Query zur Index-Nutzung Analyse
/*
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_tup_read,
    idx_tup_fetch,
    idx_scan
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
*/

-- Query zur Table-Größe Analyse
/*
SELECT 
    tablename,
    pg_size_pretty(pg_total_relation_size(tablename::regclass)) as total_size,
    pg_size_pretty(pg_relation_size(tablename::regclass)) as table_size,
    pg_size_pretty(pg_total_relation_size(tablename::regclass) - pg_relation_size(tablename::regclass)) as index_size
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(tablename::regclass) DESC;
*/