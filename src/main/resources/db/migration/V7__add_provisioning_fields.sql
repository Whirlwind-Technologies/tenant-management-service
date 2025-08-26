-- V7__add_provisioning_fields.sql
-- Add provisioning-related fields to support database/schema provisioning

-- Add provisioning timestamp fields to tenants table
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS provisioned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS provisioned_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deprovisioned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS migrated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_provisioning_error TEXT,
    ADD COLUMN IF NOT EXISTS provisioning_retry_count INTEGER DEFAULT 0;

-- Add database credential fields
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS database_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS encrypted_database_password TEXT;

-- Update status values to include new provisioning statuses
-- Note: This is handled by the enum in code, but we should ensure valid values
ALTER TABLE tenants
DROP CONSTRAINT IF EXISTS check_tenant_status;

ALTER TABLE tenants
    ADD CONSTRAINT check_tenant_status
        CHECK (status IN (
                          'PENDING_VERIFICATION',
                          'PROVISIONING',
                          'DATABASE_CREATED',
                          'SCHEMA_CREATED',
                          'READY',
                          'ACTIVE',
                          'TRIAL',
                          'SUSPENDED',
                          'DEACTIVATED',
                          'MARKED_FOR_DELETION',
                          'DELETED',
                          'PROVISIONING_FAILED',
                          'CREATION_FAILED',
                          'DEPROVISIONED'
            ));

-- Create index on provisioning status for monitoring
CREATE INDEX IF NOT EXISTS idx_tenants_provisioning_status
    ON tenants(status)
    WHERE status IN ('PROVISIONING', 'PROVISIONING_FAILED', 'CREATION_FAILED');

-- Create index on provisioned_at for reporting
CREATE INDEX IF NOT EXISTS idx_tenants_provisioned_at
    ON tenants(provisioned_at DESC);

-- Create provisioning audit table for tracking provisioning operations
CREATE TABLE IF NOT EXISTS tenant_provisioning_audit (
                                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    operation VARCHAR(50) NOT NULL, -- CREATE_DATABASE, CREATE_SCHEMA, DROP_DATABASE, etc.
    status VARCHAR(30) NOT NULL, -- STARTED, SUCCESS, FAILED
    isolation_strategy VARCHAR(50),
    resource_name VARCHAR(255), -- database or schema name
    error_message TEXT,
    duration_ms BIGINT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    metadata JSONB,

    CONSTRAINT uk_provisioning_operation UNIQUE (tenant_id, operation, started_at)
    );

-- Create indexes for provisioning audit
CREATE INDEX idx_provisioning_audit_tenant ON tenant_provisioning_audit(tenant_id);
CREATE INDEX idx_provisioning_audit_status ON tenant_provisioning_audit(status);
CREATE INDEX idx_provisioning_audit_started ON tenant_provisioning_audit(started_at DESC);

-- Create function to track provisioning operations
CREATE OR REPLACE FUNCTION track_provisioning_operation(
    p_tenant_id UUID,
    p_operation VARCHAR,
    p_status VARCHAR,
    p_resource_name VARCHAR DEFAULT NULL,
    p_error_message TEXT DEFAULT NULL,
    p_metadata JSONB DEFAULT '{}'::JSONB
) RETURNS UUID AS $$
DECLARE
v_audit_id UUID;
BEGIN
INSERT INTO tenant_provisioning_audit (
    tenant_id,
    operation,
    status,
    resource_name,
    error_message,
    metadata,
    started_at
) VALUES (
             p_tenant_id,
             p_operation,
             p_status,
             p_resource_name,
             p_error_message,
             p_metadata,
             CURRENT_TIMESTAMP
         ) RETURNING id INTO v_audit_id;

RETURN v_audit_id;
END;
$$ LANGUAGE plpgsql;

-- Create function to update provisioning operation status
CREATE OR REPLACE FUNCTION update_provisioning_status(
    p_audit_id UUID,
    p_status VARCHAR,
    p_error_message TEXT DEFAULT NULL
) RETURNS BOOLEAN AS $$
BEGIN
UPDATE tenant_provisioning_audit
SET status = p_status,
    error_message = COALESCE(p_error_message, error_message),
    completed_at = CURRENT_TIMESTAMP,
    duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - started_at)) * 1000
WHERE id = p_audit_id;

RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- Create view for monitoring active provisioning operations
CREATE OR REPLACE VIEW v_active_provisioning AS
SELECT
    t.id as tenant_id,
    t.tenant_code,
    t.name as tenant_name,
    t.organization_type,
    t.status as tenant_status,
    t.isolation_strategy,
    tpa.operation,
    tpa.status as operation_status,
    tpa.resource_name,
    tpa.started_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - tpa.started_at)) as elapsed_seconds
FROM tenants t
         JOIN tenant_provisioning_audit tpa ON t.id = tpa.tenant_id
WHERE tpa.status = 'STARTED'
  AND tpa.completed_at IS NULL
ORDER BY tpa.started_at DESC;

-- Create view for failed provisioning operations
CREATE OR REPLACE VIEW v_failed_provisioning AS
SELECT
    t.id as tenant_id,
    t.tenant_code,
    t.name as tenant_name,
    t.organization_type,
    t.isolation_strategy,
    tpa.operation,
    tpa.error_message,
    tpa.started_at,
    tpa.completed_at,
    tpa.duration_ms
FROM tenants t
         JOIN tenant_provisioning_audit tpa ON t.id = tpa.tenant_id
WHERE tpa.status = 'FAILED'
ORDER BY tpa.completed_at DESC
    LIMIT 100;

-- Add comments
COMMENT ON TABLE tenant_provisioning_audit IS 'Audit trail for tenant provisioning operations';
COMMENT ON FUNCTION track_provisioning_operation IS 'Records the start of a provisioning operation';
COMMENT ON FUNCTION update_provisioning_status IS 'Updates the status of a provisioning operation';
COMMENT ON VIEW v_active_provisioning IS 'Monitor currently active provisioning operations';
COMMENT ON VIEW v_failed_provisioning IS 'Review failed provisioning operations for troubleshooting';