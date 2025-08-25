-- V5__add_tenant_isolation.sql
-- Add tenant data isolation configuration and management

-- Add isolation strategy columns to tenants table
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS isolation_strategy VARCHAR(50);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS database_name VARCHAR(100);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS schema_name VARCHAR(100);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS database_server VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS database_port INTEGER;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS connection_pool_size INTEGER;

-- Set default isolation strategy based on organization type
UPDATE tenants SET isolation_strategy =
                       CASE
                           WHEN organization_type IN ('GOVERNMENT_AGENCY', 'FINANCIAL_INSTITUTION') THEN 'DATABASE_PER_TENANT'
                           WHEN organization_type = 'HEALTHCARE' THEN 'SCHEMA_PER_TENANT'
                           WHEN organization_type IN ('CORPORATION', 'ACADEMIC_INSTITUTION') THEN 'SCHEMA_PER_TENANT'
                           WHEN organization_type IN ('RESEARCH_ORGANIZATION', 'NON_PROFIT', 'STARTUP') THEN 'SHARED_SCHEMA_ROW_LEVEL'
                           WHEN organization_type = 'INDIVIDUAL' THEN 'SHARED_SCHEMA_BASIC'
                           ELSE 'SHARED_SCHEMA_ROW_LEVEL'
                           END
WHERE isolation_strategy IS NULL;

-- Create tenant database registry table
CREATE TABLE IF NOT EXISTS tenant_database_registry (
                                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE,
    isolation_strategy VARCHAR(50) NOT NULL,

    -- Database connection details
    database_host VARCHAR(255) NOT NULL,
    database_port INTEGER NOT NULL DEFAULT 5432,
    database_name VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100),
    username VARCHAR(100) NOT NULL,
    encrypted_password TEXT NOT NULL,

    -- Connection pool configuration
    min_pool_size INTEGER DEFAULT 2,
    max_pool_size INTEGER DEFAULT 10,
    connection_timeout_ms INTEGER DEFAULT 30000,
    idle_timeout_ms INTEGER DEFAULT 600000,
    max_lifetime_ms INTEGER DEFAULT 1800000,

    -- Status and monitoring
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, ACTIVE, MIGRATING, DISABLED
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    is_read_replica BOOLEAN NOT NULL DEFAULT FALSE,

    -- Provisioning details
    provisioned_at TIMESTAMP,
    provisioned_by VARCHAR(255),
    last_migration_at TIMESTAMP,
    last_backup_at TIMESTAMP,

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_registry_tenant FOREIGN KEY (tenant_id)
    REFERENCES tenants(id) ON DELETE CASCADE
    );

-- Create index for database registry
CREATE INDEX idx_registry_tenant ON tenant_database_registry(tenant_id);
CREATE INDEX idx_registry_status ON tenant_database_registry(status);

-- Function to provision schema for tenant
CREATE OR REPLACE FUNCTION provision_tenant_schema(
    p_tenant_id UUID,
    p_schema_name VARCHAR
) RETURNS BOOLEAN AS $$
DECLARE
v_sql TEXT;
    v_isolation_strategy VARCHAR;
BEGIN
    -- Get tenant isolation strategy
SELECT isolation_strategy INTO v_isolation_strategy
FROM tenants WHERE id = p_tenant_id;

IF v_isolation_strategy IN ('SCHEMA_PER_TENANT', 'HYBRID_POOL') THEN
        -- Create schema
        v_sql := format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);
EXECUTE v_sql;

-- Grant permissions
v_sql := format('GRANT ALL ON SCHEMA %I TO current_user', p_schema_name);
EXECUTE v_sql;

-- Create tenant-specific tables in the schema
v_sql := format('SET search_path TO %I', p_schema_name);
EXECUTE v_sql;

-- Create statistical data tables (example)
CREATE TABLE IF NOT EXISTS datasets (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS statistical_analyses (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID REFERENCES datasets(id),
    analysis_type VARCHAR(100),
    parameters JSONB,
    results JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Reset search path
SET search_path TO public;

        -- Update tenant record
UPDATE tenants
SET schema_name = p_schema_name,
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_tenant_id;

RETURN TRUE;
END IF;

RETURN FALSE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to provision database for tenant (for DATABASE_PER_TENANT strategy)
CREATE OR REPLACE FUNCTION provision_tenant_database(
    p_tenant_id UUID,
    p_database_name VARCHAR
) RETURNS BOOLEAN AS $$
DECLARE
v_isolation_strategy VARCHAR;
    v_db_exists BOOLEAN;
BEGIN
    -- Get tenant isolation strategy
SELECT isolation_strategy INTO v_isolation_strategy
FROM tenants WHERE id = p_tenant_id;

IF v_isolation_strategy = 'DATABASE_PER_TENANT' THEN
        -- Check if database exists
SELECT EXISTS(
    SELECT 1 FROM pg_database WHERE datname = p_database_name
) INTO v_db_exists;

IF NOT v_db_exists THEN
            -- Note: CREATE DATABASE cannot be executed in a transaction block
            -- This would need to be handled by application code
            RAISE NOTICE 'Database % needs to be created externally', p_database_name;

            -- Update tenant record
UPDATE tenants
SET database_name = p_database_name,
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_tenant_id;

RETURN TRUE;
END IF;
END IF;

RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Row Level Security for shared schema strategies
-- Note: These tables will be created in tenant schemas, not in public schema
-- So we'll create them here only if using shared schema strategy

-- Create shared tables for SHARED_SCHEMA strategies (only if they don't exist)
CREATE TABLE IF NOT EXISTS datasets (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
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

CREATE TABLE IF NOT EXISTS statistical_analyses (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    dataset_id UUID REFERENCES datasets(id) ON DELETE CASCADE,
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

-- Create indexes for shared tables
CREATE INDEX IF NOT EXISTS idx_datasets_tenant ON datasets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_analyses_tenant ON statistical_analyses(tenant_id);

-- Enable row level security for shared schema strategy
ALTER TABLE datasets ENABLE ROW LEVEL SECURITY;
ALTER TABLE statistical_analyses ENABLE ROW LEVEL SECURITY;

-- Create RLS policies (example for shared tables)
DROP POLICY IF EXISTS tenant_isolation_policy ON datasets;
CREATE POLICY tenant_isolation_policy ON datasets
    FOR ALL
    USING (tenant_id = get_current_tenant_id());

DROP POLICY IF EXISTS tenant_isolation_policy ON statistical_analyses;
CREATE POLICY tenant_isolation_policy ON statistical_analyses
    FOR ALL
    USING (tenant_id = get_current_tenant_id());

-- View for tenant isolation monitoring
CREATE OR REPLACE VIEW v_tenant_isolation_status AS
SELECT
    t.id,
    t.tenant_code,
    t.name,
    t.organization_type,
    t.isolation_strategy,
    t.database_name,
    t.schema_name,
    tdr.status as registry_status,
    tdr.database_host,
    tdr.max_pool_size,
    tdr.provisioned_at,
    tdr.last_backup_at,
    CASE
        WHEN t.isolation_strategy = 'DATABASE_PER_TENANT' THEN 'Highest'
        WHEN t.isolation_strategy = 'SCHEMA_PER_TENANT' THEN 'High'
        WHEN t.isolation_strategy = 'SHARED_SCHEMA_ROW_LEVEL' THEN 'Medium'
        WHEN t.isolation_strategy = 'HYBRID_POOL' THEN 'Medium-High'
        ELSE 'Low'
        END as isolation_level
FROM tenants t
         LEFT JOIN tenant_database_registry tdr ON t.id = tdr.tenant_id
WHERE t.is_deleted = FALSE;

-- Stored procedure to migrate tenant to different isolation strategy
CREATE OR REPLACE FUNCTION migrate_tenant_isolation(
    p_tenant_id UUID,
    p_new_strategy VARCHAR,
    p_target_database VARCHAR DEFAULT NULL,
    p_target_schema VARCHAR DEFAULT NULL
) RETURNS TABLE(
    success BOOLEAN,
    message TEXT,
    migration_id UUID
) AS $$
DECLARE
v_migration_id UUID;
    v_current_strategy VARCHAR;
BEGIN
    -- Get current strategy
SELECT isolation_strategy INTO v_current_strategy
FROM tenants WHERE id = p_tenant_id;

-- Generate migration ID
v_migration_id := gen_random_uuid();

    -- Log migration start
INSERT INTO audit_logs (
    tenant_id, action, entity_type, entity_id,
    old_values, new_values, timestamp
) VALUES (
             p_tenant_id, 'ISOLATION_MIGRATION_START', 'TENANT', p_tenant_id::TEXT,
             jsonb_build_object('strategy', v_current_strategy),
             jsonb_build_object('strategy', p_new_strategy, 'target_database', p_target_database, 'target_schema', p_target_schema),
             CURRENT_TIMESTAMP
         );

-- Update tenant isolation strategy
UPDATE tenants
SET isolation_strategy = p_new_strategy,
    database_name = COALESCE(p_target_database, database_name),
    schema_name = COALESCE(p_target_schema, schema_name),
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_tenant_id;

-- Update database registry
UPDATE tenant_database_registry
SET status = 'MIGRATING',
    last_migration_at = CURRENT_TIMESTAMP
WHERE tenant_id = p_tenant_id;

RETURN QUERY
SELECT
    TRUE as success,
    format('Migration initiated from %s to %s', v_current_strategy, p_new_strategy) as message,
    v_migration_id as migration_id;
END;
$$ LANGUAGE plpgsql;

-- Add comments
COMMENT ON TABLE tenant_database_registry IS 'Registry of tenant-specific database configurations for multi-tenant isolation';
COMMENT ON FUNCTION provision_tenant_schema IS 'Provisions a new schema for tenant with SCHEMA_PER_TENANT isolation';
COMMENT ON FUNCTION provision_tenant_database IS 'Provisions a new database for tenant with DATABASE_PER_TENANT isolation';
COMMENT ON VIEW v_tenant_isolation_status IS 'Monitoring view for tenant isolation strategies and status';