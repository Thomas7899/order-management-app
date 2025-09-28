-- ===== ORDER MANAGEMENT DATABASE INITIALIZATION =====
-- Professional PostgreSQL Setup Script
-- Author: Thomas Osterlehner

-- Create development database with proper settings
CREATE DATABASE order_management_dev WITH
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TEMPLATE = template0;

-- Connect to the development database
\c order_management_dev;

-- Create extensions for advanced PostgreSQL features
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Create application schema
CREATE SCHEMA IF NOT EXISTS order_management;

-- Create application user with limited privileges
DO
$do$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'order_app_user') THEN
      CREATE ROLE order_app_user WITH
          LOGIN
          NOSUPERUSER
          NOCREATEDB
          NOCREATEROLE
          PASSWORD 'app_user_password';
   END IF;
END
$do$;

-- Grant necessary privileges
GRANT CONNECT ON DATABASE order_management_dev TO order_app_user;
GRANT USAGE ON SCHEMA order_management TO order_app_user;
GRANT CREATE ON SCHEMA order_management TO order_app_user;

-- Create indexes for performance (will be populated by Hibernate)
-- These will be created automatically by JPA but listed here for documentation

-- Sample data will be loaded by Spring Boot DataLoader class

-- Performance monitoring setup
-- Create a function to monitor query performance
CREATE OR REPLACE FUNCTION get_query_stats()
RETURNS TABLE(
    query_text text,
    calls bigint,
    total_time double precision,
    mean_time double precision
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        substr(pg_stat_statements.query, 1, 50) as query_text,
        pg_stat_statements.calls,
        pg_stat_statements.total_exec_time,
        pg_stat_statements.mean_exec_time
    FROM pg_stat_statements
    WHERE pg_stat_statements.query LIKE '%order_management%'
    ORDER BY pg_stat_statements.mean_exec_time DESC
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;