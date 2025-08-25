-- setup-database.sql
-- Initial database setup script for NNIPA Tenant Management Service
-- Run this script as a PostgreSQL superuser before starting the application

-- ============================================================================
-- DATABASE SETUP
-- ============================================================================

-- Create main database for tenant management service
DROP DATABASE IF EXISTS tenant_db;
CREATE DATABASE tenant_db
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Create development database
DROP DATABASE IF EXISTS tenant_db_dev;
CREATE DATABASE tenant_db_dev
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Create test database
DROP DATABASE IF EXISTS tenant_db_test;
CREATE DATABASE tenant_db_test
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- ============================================================================
-- USER SETUP
-- ============================================================================

-- Create application user for production
DROP USER IF EXISTS tenant_user;
CREATE USER tenant_user WITH
    PASSWORD 'tenant_pass'
    CREATEDB
    CREATEROLE
    REPLICATION;

-- Create development user
DROP USER IF EXISTS tenant_dev;
CREATE USER tenant_dev WITH
    PASSWORD 'dev_password'
    CREATEDB
    CREATEROLE;

-- Create test user
DROP USER IF EXISTS tenant_test;
CREATE USER tenant_test WITH
    PASSWORD 'test_password'
    CREATEDB;

-- ============================================================================
-- GRANT PERMISSIONS
-- ============================================================================

-- Grant permissions on production database
GRANT ALL PRIVILEGES ON DATABASE tenant_db TO tenant_user;
ALTER DATABASE tenant_db OWNER TO tenant_user;

-- Grant permissions on development database
GRANT ALL PRIVILEGES ON DATABASE tenant_db_dev TO tenant_dev;
ALTER DATABASE tenant_db_dev OWNER TO tenant_dev;

-- Grant permissions on test database
GRANT ALL PRIVILEGES ON DATABASE tenant_db_test TO tenant_test;
ALTER DATABASE tenant_db_test OWNER TO tenant_test;

-- ============================================================================
-- CONNECT TO EACH DATABASE AND SET UP EXTENSIONS
-- ============================================================================

-- Setup production database
\c tenant_db tenant_user

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";      -- For UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";       -- For encryption
CREATE EXTENSION IF NOT EXISTS "tablefunc";      -- For crosstab queries
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements"; -- For query performance monitoring

-- Create schema for tenant isolation
CREATE SCHEMA IF NOT EXISTS tenant_management;
GRANT ALL ON SCHEMA tenant_management TO tenant_user;

-- Setup development database
\c tenant_db_dev tenant_dev

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "tablefunc";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create schema for tenant isolation
CREATE SCHEMA IF NOT EXISTS tenant_management;
GRANT ALL ON SCHEMA tenant_management TO tenant_dev;

-- Setup test database
\c tenant_db_test tenant_test

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "tablefunc";

-- ============================================================================
-- ROLES FOR MULTI-TENANT ISOLATION
-- ============================================================================

\c tenant_db tenant_user

-- Create role for tenant database administrators
DROP ROLE IF EXISTS tenant_db_admin;
CREATE ROLE tenant_db_admin WITH
    CREATEDB
    CREATEROLE
    LOGIN
    REPLICATION
    BYPASSRLS;

-- Create role for tenant schema owners
DROP ROLE IF EXISTS tenant_schema_owner;
CREATE ROLE tenant_schema_owner WITH
    NOCREATEDB
    NOCREATEROLE
    LOGIN
    NOREPLICATION;

-- Create role for tenant application users
DROP ROLE IF EXISTS tenant_app_user;
CREATE ROLE tenant_app_user WITH
    NOCREATEDB
    NOCREATEROLE
    LOGIN
    NOREPLICATION;

-- Grant appropriate permissions
GRANT CONNECT ON DATABASE tenant_db TO tenant_schema_owner;
GRANT CONNECT ON DATABASE tenant_db TO tenant_app_user;

-- ============================================================================
-- TEMPLATE DATABASE FOR TENANT ISOLATION
-- ============================================================================

-- Create template database for DATABASE_PER_TENANT strategy
DROP DATABASE IF EXISTS tenant_template;
CREATE DATABASE tenant_template
    WITH
    OWNER = tenant_user
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1
    IS_TEMPLATE = true;

\c tenant_template tenant_user

-- Enable extensions in template
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create base schema structure for tenants
CREATE SCHEMA IF NOT EXISTS app;

-- Create base tables that all tenant databases will have
CREATE TABLE app.datasets (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              name VARCHAR(255) NOT NULL,
                              description TEXT,
                              data_type VARCHAR(50),
                              source VARCHAR(255),
                              size_bytes BIGINT,
                              row_count BIGINT,
                              column_count INTEGER,
                              metadata JSONB,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP,
                              created_by VARCHAR(255),
                              is_public BOOLEAN DEFAULT FALSE
);

CREATE TABLE app.statistical_analyses (
                                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                          dataset_id UUID REFERENCES app.datasets(id) ON DELETE CASCADE,
                                          analysis_type VARCHAR(100) NOT NULL,
                                          analysis_name VARCHAR(255),
                                          description TEXT,
                                          parameters JSONB,
                                          results JSONB,
                                          status VARCHAR(30),
                                          started_at TIMESTAMP,
                                          completed_at TIMESTAMP,
                                          error_message TEXT,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(255)
);

CREATE TABLE app.reports (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             analysis_id UUID REFERENCES app.statistical_analyses(id) ON DELETE CASCADE,
                             report_name VARCHAR(255) NOT NULL,
                             report_type VARCHAR(50),
                             format VARCHAR(20),
                             content TEXT,
                             file_path VARCHAR(500),
                             metadata JSONB,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             created_by VARCHAR(255)
);

-- Create indexes
CREATE INDEX idx_datasets_name ON app.datasets(name);
CREATE INDEX idx_datasets_created ON app.datasets(created_at DESC);
CREATE INDEX idx_analyses_type ON app.statistical_analyses(analysis_type);
CREATE INDEX idx_analyses_status ON app.statistical_analyses(status);
CREATE INDEX idx_analyses_dataset ON app.statistical_analyses(dataset_id);
CREATE INDEX idx_reports_analysis ON app.reports(analysis_id);

-- ============================================================================
-- MONITORING AND MAINTENANCE
-- ============================================================================

\c tenant_db tenant_user

-- Create monitoring schema
CREATE SCHEMA IF NOT EXISTS monitoring;

-- Table to track database sizes
CREATE TABLE monitoring.database_sizes (
                                           id SERIAL PRIMARY KEY,
                                           database_name VARCHAR(255) NOT NULL,
                                           size_bytes BIGINT,
                                           table_count INTEGER,
                                           connection_count INTEGER,
                                           measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to track schema sizes
CREATE TABLE monitoring.schema_sizes (
                                         id SERIAL PRIMARY KEY,
                                         database_name VARCHAR(255) NOT NULL,
                                         schema_name VARCHAR(255) NOT NULL,
                                         size_bytes BIGINT,
                                         table_count INTEGER,
                                         row_count BIGINT,
                                         measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Function to monitor database sizes
CREATE OR REPLACE FUNCTION monitoring.capture_database_sizes()
RETURNS void AS $$
BEGIN
INSERT INTO monitoring.database_sizes (database_name, size_bytes, table_count, connection_count)
SELECT
    datname,
    pg_database_size(datname),
    (SELECT COUNT(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')),
    (SELECT COUNT(*) FROM pg_stat_activity WHERE datname = d.datname)
FROM pg_database d
WHERE datname LIKE 'tenant_%' OR datname LIKE 'nnipa_%';
END;
$$ LANGUAGE plpgsql;

-- Schedule monitoring (would be called by external scheduler)
-- SELECT monitoring.capture_database_sizes();

-- ============================================================================
-- SECURITY POLICIES FOR SHARED SCHEMA
-- ============================================================================

\c tenant_db_dev tenant_dev

-- Enable row level security for shared schema strategy
ALTER TABLE public.datasets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.statistical_analyses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

-- Create policies (these will be created when tables exist)
-- These are examples and will be properly created by Flyway migrations

-- ============================================================================
-- UTILITY FUNCTIONS
-- ============================================================================

-- Function to create a new tenant database (for DATABASE_PER_TENANT)
CREATE OR REPLACE FUNCTION create_tenant_database(
    p_tenant_code VARCHAR,
    p_tenant_id UUID
) RETURNS TEXT AS $$
DECLARE
v_db_name TEXT;
    v_username TEXT;
    v_password TEXT;
BEGIN
    v_db_name := 'tenant_' || LOWER(REGEXP_REPLACE(p_tenant_code, '[^a-zA-Z0-9]', '_', 'g'));
    v_username := 'user_' || REPLACE(p_tenant_id::TEXT, '-', '');
    v_password := encode(pgcrypto.gen_random_bytes(16), 'hex');

    -- Return connection string (actual database creation would be done externally)
RETURN format('postgresql://%s:%s@localhost:5432/%s', v_username, v_password, v_db_name);
END;
$$ LANGUAGE plpgsql;

-- Function to create a new tenant schema (for SCHEMA_PER_TENANT)
CREATE OR REPLACE FUNCTION create_tenant_schema(
    p_tenant_code VARCHAR,
    p_tenant_id UUID
) RETURNS TEXT AS $$
DECLARE
v_schema_name TEXT;
BEGIN
    v_schema_name := 'tenant_' || LOWER(REGEXP_REPLACE(p_tenant_code, '[^a-zA-Z0-9]', '_', 'g'));

    -- Create schema
EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', v_schema_name);

-- Grant permissions
EXECUTE format('GRANT ALL ON SCHEMA %I TO tenant_app_user', v_schema_name);

RETURN v_schema_name;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- FINAL SETUP
-- ============================================================================

-- Verify setup
\c tenant_db
SELECT 'Production database created successfully' AS status;

\c tenant_db_dev
SELECT 'Development database created successfully' AS status;

\c tenant_db_test
SELECT 'Test database created successfully' AS status;

-- List all databases
\c postgres
SELECT datname, pg_size_pretty(pg_database_size(datname)) as size
FROM pg_database
WHERE datname LIKE 'tenant%'
ORDER BY datname;

-- ============================================================================
-- NOTES FOR DEPLOYMENT
-- ============================================================================
-- 1. Update passwords in production environment
-- 2. Configure pg_hba.conf for appropriate access control
-- 3. Set up SSL certificates for production
-- 4. Configure backup and recovery procedures
-- 5. Set up monitoring and alerting
-- 6. Configure connection pooling (PgBouncer recommended)
-- 7. Set up replication for high availability
-- ============================================================================