-- order-management/src/main/resources/db/migration/V2__ai_improvements.sql
-- AI-Verbesserungen: Schema-Updates für optimierte Embedding-Speicherung

-- 1. Erweitere review_embeddings Tabelle um Metadaten
ALTER TABLE review_embeddings 
ADD COLUMN IF NOT EXISTS source_text TEXT,
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();

-- 2. Index für content_hash zur Idempotenz-Prüfung
CREATE INDEX IF NOT EXISTS idx_review_embeddings_hash 
ON review_embeddings (content_hash);

-- 3. HNSW Index für schnelle Approximate Nearest Neighbor Suche
-- HNSW ist schneller als IVFFlat für die meisten Anwendungsfälle
-- m=16: Anzahl der Verbindungen pro Knoten (höher = genauer, langsamer Build)
-- ef_construction=64: Suchtiefe beim Index-Aufbau
CREATE INDEX IF NOT EXISTS idx_review_embeddings_hnsw 
ON review_embeddings 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Optional: IVFFlat Index als Alternative für sehr große Datasets
-- CREATE INDEX IF NOT EXISTS idx_review_embeddings_ivfflat
-- ON review_embeddings 
-- USING ivfflat (embedding vector_cosine_ops)
-- WITH (lists = 100);

-- 5. Statistiken aktualisieren für Query-Optimierung
ANALYZE review_embeddings;

-- 6. Kommentar zur Index-Wahl:
-- HNSW (Hierarchical Navigable Small World):
--   + Schnellere Suche (O(log n))
--   + Keine Vorberechnung von Listen nötig
--   - Mehr Speicherplatz
--   - Langsamerer Build
--
-- IVFFlat (Inverted File Index):
--   + Weniger Speicherplatz
--   + Schnellerer Build
--   - Langsamer bei kleinen Datasets
--   - Erfordert gute lists-Parameter-Wahl
