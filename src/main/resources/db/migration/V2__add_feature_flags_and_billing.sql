-- V2__add_feature_flags_and_billing.sql
-- Add feature flags, billing details, and usage tracking tables

-- Create feature flags table
CREATE TABLE feature_flags (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               version BIGINT NOT NULL DEFAULT 0,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               created_by VARCHAR(255),
                               updated_at TIMESTAMP,
                               updated_by VARCHAR(255),
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               deleted_at TIMESTAMP,
                               deleted_by VARCHAR(255),

                               tenant_id UUID NOT NULL,
                               feature_code VARCHAR(100) NOT NULL,
                               feature_name VARCHAR(255) NOT NULL,
                               description TEXT,
                               category VARCHAR(50),

    -- Status
                               is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                               is_beta BOOLEAN NOT NULL DEFAULT FALSE,
                               is_experimental BOOLEAN NOT NULL DEFAULT FALSE,

    -- Requirements
                               required_plan VARCHAR(30),
                               required_organization_type VARCHAR(50),

    -- Time-based access
                               enabled_from TIMESTAMP,
                               enabled_until TIMESTAMP,
                               trial_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                               trial_days INTEGER,

    -- Usage limits
                               usage_limit INTEGER,
                               current_usage INTEGER DEFAULT 0,
                               reset_frequency VARCHAR(20),
                               last_reset_at TIMESTAMP,

    -- Configuration
                               config_json TEXT,
                               metadata_json TEXT,

    -- Approval
                               requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
                               approved_by VARCHAR(255),
                               approved_at TIMESTAMP,
                               approval_notes TEXT,

    -- Dependencies
                               depends_on VARCHAR(500),
                               conflicts_with VARCHAR(500),

    -- Rollout
                               rollout_percentage INTEGER DEFAULT 100,
                               rollout_group VARCHAR(50),

    -- Tracking
                               first_enabled_at TIMESTAMP,
                               last_enabled_at TIMESTAMP,
                               total_enabled_days INTEGER DEFAULT 0,
                               toggle_count INTEGER DEFAULT 0,

                               CONSTRAINT fk_feature_tenant FOREIGN KEY (tenant_id)
                                   REFERENCES tenants(id) ON DELETE CASCADE,
                               CONSTRAINT uk_feature_tenant_code UNIQUE (tenant_id, feature_code)
);

-- Create indexes for feature flags
CREATE INDEX idx_feature_tenant ON feature_flags(tenant_id);
CREATE INDEX idx_feature_code ON feature_flags(feature_code);
CREATE INDEX idx_feature_enabled ON feature_flags(is_enabled);
CREATE INDEX idx_feature_tenant_code ON feature_flags(tenant_id, feature_code);

-- Create billing details table
CREATE TABLE billing_details (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 version BIGINT NOT NULL DEFAULT 0,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 created_by VARCHAR(255),
                                 updated_at TIMESTAMP,
                                 updated_by VARCHAR(255),
                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                 deleted_at TIMESTAMP,
                                 deleted_by VARCHAR(255),

                                 subscription_id UUID NOT NULL UNIQUE,

    -- Billing contact
                                 billing_contact_name VARCHAR(255),
                                 billing_contact_email VARCHAR(255) NOT NULL,
                                 billing_contact_phone VARCHAR(50),

    -- Billing address
                                 billing_address_line1 VARCHAR(255),
                                 billing_address_line2 VARCHAR(255),
                                 billing_city VARCHAR(100),
                                 billing_state_province VARCHAR(100),
                                 billing_postal_code VARCHAR(20),
                                 billing_country VARCHAR(2),

    -- Tax information
                                 tax_id VARCHAR(50),
                                 vat_number VARCHAR(50),
                                 tax_exempt BOOLEAN NOT NULL DEFAULT FALSE,
                                 tax_exempt_certificate VARCHAR(255),

    -- Payment method
                                 payment_method_type VARCHAR(50),
                                 card_last_four VARCHAR(4),
                                 card_brand VARCHAR(50),
                                 card_exp_month INTEGER,
                                 card_exp_year INTEGER,
                                 bank_name VARCHAR(255),
                                 bank_account_last_four VARCHAR(4),
                                 bank_routing_number VARCHAR(20),

    -- Invoice settings
                                 invoice_prefix VARCHAR(20),
                                 invoice_notes TEXT,
                                 invoice_footer TEXT,
                                 send_invoice_automatically BOOLEAN NOT NULL DEFAULT TRUE,
                                 invoice_delivery_email VARCHAR(255),

    -- Additional
                                 purchase_order_required BOOLEAN NOT NULL DEFAULT FALSE,
                                 payment_processor_customer_id VARCHAR(255),
                                 payment_processor_subscription_id VARCHAR(255),
                                 notes TEXT,

                                 CONSTRAINT fk_billing_subscription FOREIGN KEY (subscription_id)
                                     REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create index for billing details
CREATE INDEX idx_billing_subscription ON billing_details(subscription_id);

-- Create usage records table
CREATE TABLE usage_records (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               version BIGINT NOT NULL DEFAULT 0,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               created_by VARCHAR(255),
                               updated_at TIMESTAMP,
                               updated_by VARCHAR(255),
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               deleted_at TIMESTAMP,
                               deleted_by VARCHAR(255),

                               subscription_id UUID NOT NULL,
                               usage_date DATE NOT NULL,
                               metric_name VARCHAR(100) NOT NULL,
                               metric_category VARCHAR(50),

    -- Quantities and billing
                               quantity DECIMAL(15,4) NOT NULL,
                               unit VARCHAR(50),
                               rate DECIMAL(10,4),
                               amount DECIMAL(10,2),

    -- Billing flags
                               is_billable BOOLEAN NOT NULL DEFAULT TRUE,
                               is_overage BOOLEAN NOT NULL DEFAULT FALSE,
                               included_quantity DECIMAL(15,4),
                               overage_quantity DECIMAL(15,4),

    -- Metadata
                               description TEXT,
                               metadata_json TEXT,

    -- Tracking
                               recorded_at TIMESTAMP NOT NULL,
                               billed_at TIMESTAMP,
                               invoice_id VARCHAR(100),

                               CONSTRAINT fk_usage_subscription FOREIGN KEY (subscription_id)
                                   REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create indexes for usage records
CREATE INDEX idx_usage_subscription ON usage_records(subscription_id);
CREATE INDEX idx_usage_date ON usage_records(usage_date);
CREATE INDEX idx_usage_metric ON usage_records(metric_name);
CREATE INDEX idx_usage_subscription_date ON usage_records(subscription_id, usage_date);

-- Create audit log table for compliance
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            tenant_id UUID,
                            user_id VARCHAR(255),
                            action VARCHAR(100) NOT NULL,
                            entity_type VARCHAR(50),
                            entity_id VARCHAR(255),
                            old_values JSONB,
                            new_values JSONB,
                            ip_address VARCHAR(45),
                            user_agent TEXT,
                            session_id VARCHAR(255),
                            timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id)
                                REFERENCES tenants(id) ON DELETE SET NULL
);

-- Create indexes for audit logs
CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

-- Add comments for documentation
COMMENT ON TABLE feature_flags IS 'Tenant-specific feature flags with tier-based and time-based access control';
COMMENT ON TABLE billing_details IS 'Detailed billing information for subscription management';
COMMENT ON TABLE usage_records IS 'Usage tracking for metered billing and resource monitoring';
COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for compliance and security';
COMMENT ON COLUMN feature_flags.category IS 'ANALYTICS, SECURITY, INTEGRATION, UI, DATA';
COMMENT ON COLUMN usage_records.metric_category IS 'COMPUTE, STORAGE, API, DATA, USERS';