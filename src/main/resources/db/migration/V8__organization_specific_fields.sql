-- V7__organization_specific_fields.sql
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS security_level VARCHAR(20) DEFAULT 'MEDIUM';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_tenants_org_type ON tenants(organization_type);
CREATE INDEX IF NOT EXISTS idx_tenants_security_level ON tenants(security_level);
CREATE INDEX IF NOT EXISTS idx_tenants_metadata ON tenants USING gin(metadata);