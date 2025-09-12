-- V1__init_tenant_schema.sql
-- Initial schema for Tenant Management Service

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create tenants table
CREATE TABLE tenants (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         version BIGINT NOT NULL DEFAULT 0,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         created_by VARCHAR(255),
                         updated_at TIMESTAMP,
                         updated_by VARCHAR(255),
                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                         deleted_at TIMESTAMP,
                         deleted_by VARCHAR(255),

    -- Core fields
                         tenant_code VARCHAR(50) NOT NULL UNIQUE,
                         name VARCHAR(255) NOT NULL,
                         display_name VARCHAR(255),
                         organization_type VARCHAR(50) NOT NULL,
                         isolation_strategy VARCHAR(50) NOT NULL,
                         status VARCHAR(30) NOT NULL,

    -- Contact information
                         organization_email VARCHAR(255),
                         organization_phone VARCHAR(50),
                         organization_website VARCHAR(500),

    -- Address
                         address_line1 VARCHAR(255),
                         address_line2 VARCHAR(255),
                         city VARCHAR(100),
                         state_province VARCHAR(100),
                         postal_code VARCHAR(20),
                         country VARCHAR(2),

    -- Status and lifecycle
                         activated_at TIMESTAMP,
                         suspended_at TIMESTAMP,
                         suspension_reason VARCHAR(500),
                         trial_ends_at TIMESTAMP,

    -- Verification
                         is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                         verified_at TIMESTAMP,
                         verification_document VARCHAR(500),

    -- Usage limits
                         max_users INTEGER,
                         max_projects INTEGER,
                         storage_quota_gb INTEGER,
                         api_rate_limit INTEGER,

    -- Hierarchy
                         parent_tenant_id UUID,

                         CONSTRAINT fk_parent_tenant FOREIGN KEY (parent_tenant_id)
                             REFERENCES tenants(id) ON DELETE CASCADE
);

-- Create indexes for tenants
CREATE INDEX idx_tenant_code ON tenants(tenant_code);
CREATE INDEX idx_tenant_status ON tenants(status);
CREATE INDEX idx_tenant_org_type ON tenants(organization_type);
CREATE INDEX idx_tenant_parent ON tenants(parent_tenant_id);
CREATE INDEX idx_tenant_created ON tenants(created_at);
CREATE INDEX idx_tenant_deleted ON tenants(is_deleted);

-- Create subscriptions table
CREATE TABLE subscriptions (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               version BIGINT NOT NULL DEFAULT 0,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               created_by VARCHAR(255),
                               updated_at TIMESTAMP,
                               updated_by VARCHAR(255),
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               deleted_at TIMESTAMP,
                               deleted_by VARCHAR(255),

                               tenant_id UUID NOT NULL UNIQUE,
                               plan VARCHAR(30) NOT NULL,
                               subscription_status VARCHAR(30) NOT NULL,

    -- Pricing
                               monthly_price DECIMAL(10,2),
                               annual_price DECIMAL(10,2),
                               currency VARCHAR(3) DEFAULT 'USD',
                               billing_cycle VARCHAR(20),

    -- Discount
                               discount_percentage DECIMAL(5,2),
                               discount_amount DECIMAL(10,2),
                               discount_reason VARCHAR(255),
                               discount_valid_until TIMESTAMP,

    -- Lifecycle
                               start_date TIMESTAMP NOT NULL,
                               end_date TIMESTAMP,
                               trial_start_date TIMESTAMP,
                               trial_end_date TIMESTAMP,
                               cancelled_at TIMESTAMP,
                               cancellation_reason VARCHAR(500),

    -- Renewal
                               next_renewal_date TIMESTAMP,
                               auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
                               renewal_reminder_sent BOOLEAN DEFAULT FALSE,
                               last_renewed_at TIMESTAMP,

    -- Payment
                               payment_method VARCHAR(50),
                               last_payment_date TIMESTAMP,
                               last_payment_amount DECIMAL(10,2),
                               last_payment_status VARCHAR(30),
                               failed_payment_count INTEGER DEFAULT 0,

    -- External references
                               stripe_subscription_id VARCHAR(255),
                               stripe_customer_id VARCHAR(255),

                               CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id)
                                   REFERENCES tenants(id) ON DELETE CASCADE
);

-- Create indexes for subscriptions
CREATE INDEX idx_subscription_tenant ON subscriptions(tenant_id);
CREATE INDEX idx_subscription_status ON subscriptions(subscription_status);
CREATE INDEX idx_subscription_plan ON subscriptions(plan);
CREATE INDEX idx_subscription_renewal ON subscriptions(next_renewal_date);
CREATE INDEX idx_subscription_end ON subscriptions(end_date);

-- Create feature_flags table
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
                               config_json JSONB,
                               metadata_json JSONB,

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

-- Create indexes for feature_flags
CREATE INDEX idx_feature_tenant ON feature_flags(tenant_id);
CREATE INDEX idx_feature_code ON feature_flags(feature_code);
CREATE INDEX idx_feature_enabled ON feature_flags(is_enabled);
CREATE INDEX idx_feature_category ON feature_flags(category);

-- Create billing_details table
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
                                 billing_email VARCHAR(255),
                                 billing_name VARCHAR(255),
                                 billing_phone VARCHAR(50),

    -- Billing address
                                 billing_address_line1 VARCHAR(255),
                                 billing_address_line2 VARCHAR(255),
                                 billing_city VARCHAR(100),
                                 billing_state VARCHAR(100),
                                 billing_postal_code VARCHAR(20),
                                 billing_country VARCHAR(2),

    -- Tax information
                                 tax_id VARCHAR(100),
                                 vat_number VARCHAR(100),
                                 tax_exempt BOOLEAN NOT NULL DEFAULT FALSE,
                                 tax_exempt_reason VARCHAR(255),

    -- Payment method
                                 payment_method_type VARCHAR(50),
                                 card_last_four VARCHAR(4),
                                 card_brand VARCHAR(50),
                                 card_expiry_month INTEGER,
                                 card_expiry_year INTEGER,

    -- Purchase order
                                 purchase_order_number VARCHAR(100),
                                 purchase_order_expiry DATE,

    -- Invoice preferences
                                 invoice_prefix VARCHAR(20),
                                 invoice_notes TEXT,
                                 send_invoice_email BOOLEAN NOT NULL DEFAULT TRUE,

                                 CONSTRAINT fk_billing_subscription FOREIGN KEY (subscription_id)
                                     REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create index for billing_details
CREATE INDEX idx_billing_subscription ON billing_details(subscription_id);

-- Create tenant_settings table
CREATE TABLE tenant_settings (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 version BIGINT NOT NULL DEFAULT 0,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 created_by VARCHAR(255),
                                 updated_at TIMESTAMP,
                                 updated_by VARCHAR(255),
                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                 deleted_at TIMESTAMP,
                                 deleted_by VARCHAR(255),

                                 tenant_id UUID NOT NULL UNIQUE,

    -- General settings
                                 timezone VARCHAR(50),
                                 locale VARCHAR(10),
                                 date_format VARCHAR(50),
                                 currency VARCHAR(3),

    -- Security settings
                                 enforce_mfa BOOLEAN NOT NULL DEFAULT FALSE,
                                 password_expiry_days INTEGER,
                                 session_timeout_minutes INTEGER,
                                 ip_whitelist TEXT,
                                 allowed_domains TEXT,

    -- Notification settings
                                 notification_email VARCHAR(255),
                                 send_billing_alerts BOOLEAN NOT NULL DEFAULT TRUE,
                                 send_usage_alerts BOOLEAN NOT NULL DEFAULT TRUE,
                                 send_security_alerts BOOLEAN NOT NULL DEFAULT TRUE,

    -- Branding
                                 logo_url VARCHAR(500),
                                 primary_color VARCHAR(7),
                                 secondary_color VARCHAR(7),

    -- Integration settings
                                 webhook_urls JSONB,
                                 api_keys JSONB,

    -- Custom settings
                                 custom_settings JSONB,

    -- Compliance
                                 data_retention_days INTEGER,
                                 audit_log_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                 compliance_frameworks VARCHAR(500),

                                 CONSTRAINT fk_settings_tenant FOREIGN KEY (tenant_id)
                                     REFERENCES tenants(id) ON DELETE CASCADE
);

-- Create index for tenant_settings
CREATE INDEX idx_settings_tenant ON tenant_settings(tenant_id);

-- Create usage_records table
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
                               metadata_json JSONB,

    -- Tracking
                               recorded_at TIMESTAMP NOT NULL,
                               billed_at TIMESTAMP,
                               invoice_id VARCHAR(100),

                               CONSTRAINT fk_usage_subscription FOREIGN KEY (subscription_id)
                                   REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create indexes for usage_records
CREATE INDEX idx_usage_subscription ON usage_records(subscription_id);
CREATE INDEX idx_usage_date ON usage_records(usage_date);
CREATE INDEX idx_usage_metric ON usage_records(metric_name);
CREATE INDEX idx_usage_subscription_date ON usage_records(subscription_id, usage_date);

-- Create audit_logs table for compliance
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

-- Create indexes for audit_logs
CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

-- Add comments for documentation
COMMENT ON TABLE tenants IS 'Core tenant entity representing organizations/customers';
COMMENT ON TABLE subscriptions IS 'Subscription and billing information for tenants';
COMMENT ON TABLE feature_flags IS 'Tenant-specific feature flags with granular control';
COMMENT ON TABLE billing_details IS 'Detailed billing and payment information';
COMMENT ON TABLE tenant_settings IS 'Tenant configuration and preferences';
COMMENT ON TABLE usage_records IS 'Usage tracking for metered billing';
COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for compliance';

COMMENT ON COLUMN tenants.tenant_code IS 'Unique identifier code for the tenant';
COMMENT ON COLUMN tenants.organization_type IS 'Type of organization (GOVERNMENT, CORPORATION, etc.)';
COMMENT ON COLUMN subscriptions.plan IS 'Subscription plan (FREEMIUM, BASIC, PROFESSIONAL, etc.)';
COMMENT ON COLUMN feature_flags.category IS 'Feature category (ANALYTICS, SECURITY, INTEGRATION, etc.)';