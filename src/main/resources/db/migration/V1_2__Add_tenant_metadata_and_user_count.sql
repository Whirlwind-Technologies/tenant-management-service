-- Add user_count column to tenants table
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS user_count INTEGER DEFAULT 0;

-- Create tenant_metadata table for storing key-value metadata
CREATE TABLE IF NOT EXISTS tenant_metadata (
                                               tenant_id UUID NOT NULL,
                                               metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(1000),
    PRIMARY KEY (tenant_id, metadata_key),
    CONSTRAINT fk_tenant_metadata_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
    );

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_tenant_metadata_key ON tenant_metadata(metadata_key);

-- Initialize user_count for existing tenants
UPDATE tenants SET user_count = 0 WHERE user_count IS NULL;