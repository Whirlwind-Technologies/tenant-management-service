-- V1__initial_schema.sql
-- Initial schema for NNIPA Tenant Management Service
-- Supports multi-organization types with flexible configuration

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
                         description TEXT,
                         organization_type VARCHAR(50) NOT NULL,
                         status VARCHAR(30) NOT NULL,

    -- Organization details
                         organization_email VARCHAR(255),
                         organization_phone VARCHAR(50),
                         organization_website VARCHAR(500),
                         tax_id VARCHAR(50),
                         business_license VARCHAR(100),

    -- Address
                         address_line1 VARCHAR(255),
                         address_line2 VARCHAR(255),
                         city VARCHAR(100),
                         state_province VARCHAR(100),
                         postal_code VARCHAR(20),
                         country VARCHAR(2),

    -- Security and compliance
                         data_residency_region VARCHAR(50),
                         security_level VARCHAR(20),

    -- Verification
                         is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                         verified_at TIMESTAMP,
                         verified_by VARCHAR(255),
                         verification_document VARCHAR(500),

    -- Hierarchy
                         parent_tenant_id UUID,

    -- Usage limits
                         max_users INTEGER,
                         max_projects INTEGER,
                         storage_quota_gb INTEGER,
                         api_rate_limit INTEGER,

    -- Dates
                         activated_at TIMESTAMP,
                         expires_at TIMESTAMP,
                         trial_ends_at TIMESTAMP,
                         suspended_at TIMESTAMP,
                         suspension_reason TEXT,

    -- Branding
                         logo_url VARCHAR(500),
                         primary_color VARCHAR(7),
                         secondary_color VARCHAR(7),
                         timezone VARCHAR(50) DEFAULT 'UTC',
                         locale VARCHAR(10) DEFAULT 'en_US',
                         tags TEXT,

                         CONSTRAINT fk_parent_tenant FOREIGN KEY (parent_tenant_id)
                             REFERENCES tenants(id) ON DELETE SET NULL
);

-- Create indexes for tenants
CREATE INDEX idx_tenant_code ON tenants(tenant_code);
CREATE INDEX idx_tenant_status ON tenants(status);
CREATE INDEX idx_tenant_org_type ON tenants(organization_type);
CREATE INDEX idx_tenant_created ON tenants(created_at);
CREATE INDEX idx_tenant_parent ON tenants(parent_tenant_id);
CREATE INDEX idx_tenant_deleted ON tenants(is_deleted);

-- Create tenant compliance frameworks table
CREATE TABLE tenant_compliance_frameworks (
                                              tenant_id UUID NOT NULL,
                                              framework VARCHAR(50) NOT NULL,
                                              PRIMARY KEY (tenant_id, framework),
                                              CONSTRAINT fk_tcf_tenant FOREIGN KEY (tenant_id)
                                                  REFERENCES tenants(id) ON DELETE CASCADE
);

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
                               discount_percentage DECIMAL(5,2),
                               discount_reason VARCHAR(255),

    -- Contract
                               contract_id VARCHAR(100),
                               contract_start_date TIMESTAMP,
                               contract_end_date TIMESTAMP,
                               contract_value DECIMAL(12,2),
                               purchase_order_number VARCHAR(100),

    -- Dates
                               start_date TIMESTAMP NOT NULL,
                               end_date TIMESTAMP,
                               trial_start_date TIMESTAMP,
                               trial_end_date TIMESTAMP,
                               next_renewal_date TIMESTAMP,
                               canceled_at TIMESTAMP,
                               cancellation_reason TEXT,

    -- Payment
                               payment_method VARCHAR(50),
                               payment_terms VARCHAR(50),
                               auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
                               last_payment_date TIMESTAMP,
                               last_payment_amount DECIMAL(10,2),
                               next_payment_date TIMESTAMP,
                               next_payment_amount DECIMAL(10,2),

    -- Usage-based billing
                               is_usage_based BOOLEAN NOT NULL DEFAULT FALSE,
                               base_fee DECIMAL(10,2),
                               overage_rate DECIMAL(10,4),
                               usage_cap INTEGER,

    -- Custom limits
                               custom_max_users INTEGER,
                               custom_max_projects INTEGER,
                               custom_storage_gb INTEGER,
                               custom_api_calls_per_day INTEGER,
                               custom_compute_units INTEGER,

    -- Metadata
                               external_subscription_id VARCHAR(255),
                               notes TEXT,

                               CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id)
                                   REFERENCES tenants(id) ON DELETE CASCADE
);

-- Create indexes for subscriptions
CREATE INDEX idx_subscription_tenant ON subscriptions(tenant_id);
CREATE INDEX idx_subscription_plan ON subscriptions(plan);
CREATE INDEX idx_subscription_status ON subscriptions(subscription_status);
CREATE INDEX idx_subscription_renewal ON subscriptions(next_renewal_date);

-- Create subscription add-ons table
CREATE TABLE subscription_addons (
                                     subscription_id UUID NOT NULL,
                                     addon VARCHAR(100) NOT NULL,
                                     PRIMARY KEY (subscription_id, addon),
                                     CONSTRAINT fk_addon_subscription FOREIGN KEY (subscription_id)
                                         REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create subscription custom features table
CREATE TABLE subscription_custom_features (
                                              subscription_id UUID NOT NULL,
                                              feature VARCHAR(100) NOT NULL,
                                              PRIMARY KEY (subscription_id, feature),
                                              CONSTRAINT fk_feature_subscription FOREIGN KEY (subscription_id)
                                                  REFERENCES subscriptions(id) ON DELETE CASCADE
);

-- Create tenant settings table
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
                                 default_language VARCHAR(10) DEFAULT 'en',
                                 date_format VARCHAR(50) DEFAULT 'yyyy-MM-dd',
                                 time_format VARCHAR(50) DEFAULT 'HH:mm:ss',
                                 number_format VARCHAR(50) DEFAULT '#,##0.00',
                                 currency_format VARCHAR(50) DEFAULT '$#,##0.00',

    -- Security settings
                                 password_policy TEXT,
                                 session_timeout_minutes INTEGER DEFAULT 30,
                                 mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
                                 mfa_type VARCHAR(50),
                                 ip_whitelist TEXT,
                                 allowed_domains TEXT,

    -- Data settings
                                 data_retention_days INTEGER,
                                 auto_backup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                 backup_frequency VARCHAR(20) DEFAULT 'DAILY',
                                 backup_retention_days INTEGER DEFAULT 30,
                                 data_export_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                 allowed_export_formats VARCHAR(255) DEFAULT 'CSV,JSON,EXCEL',

    -- Notification settings
                                 notification_email VARCHAR(255),
                                 billing_email VARCHAR(255),
                                 technical_email VARCHAR(255),
                                 email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                 sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                 webhook_url VARCHAR(500),
                                 webhook_secret VARCHAR(255),

    -- Integration settings
                                 sso_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                 sso_provider VARCHAR(50),
                                 sso_config TEXT,
                                 api_key VARCHAR(255),
                                 api_secret VARCHAR(255),
                                 api_rate_limit_override INTEGER,

    -- UI customization
                                 show_logo BOOLEAN NOT NULL DEFAULT TRUE,
                                 custom_css TEXT,
                                 dashboard_layout TEXT,
                                 default_dashboard VARCHAR(100),

    -- Feature toggles
                                 enable_api_access BOOLEAN NOT NULL DEFAULT TRUE,
                                 enable_data_sharing BOOLEAN NOT NULL DEFAULT FALSE,
                                 enable_public_dashboards BOOLEAN NOT NULL DEFAULT FALSE,
                                 enable_custom_reports BOOLEAN NOT NULL DEFAULT TRUE,
                                 enable_advanced_analytics BOOLEAN NOT NULL DEFAULT FALSE,

    -- JSON fields for flexible storage
                                 custom_settings JSONB,
                                 compliance_settings JSONB,
                                 workflow_settings JSONB,

                                 CONSTRAINT fk_settings_tenant FOREIGN KEY (tenant_id)
                                     REFERENCES tenants(id) ON DELETE CASCADE
);

-- Create index for tenant settings
CREATE INDEX idx_tenant_settings_tenant ON tenant_settings(tenant_id);

-- Add comments for documentation
COMMENT ON TABLE tenants IS 'Multi-organization tenant management supporting government, enterprise, academic, and individual users';
COMMENT ON TABLE subscriptions IS 'Flexible subscription management with support for various pricing models';
COMMENT ON TABLE tenant_settings IS 'Configurable settings per tenant with JSON fields for extensibility';
COMMENT ON COLUMN tenants.organization_type IS 'GOVERNMENT_AGENCY, CORPORATION, ACADEMIC_INSTITUTION, NON_PROFIT, RESEARCH_ORGANIZATION, INDIVIDUAL, STARTUP, HEALTHCARE, FINANCIAL_INSTITUTION';
COMMENT ON COLUMN tenants.status IS 'PENDING_VERIFICATION, ACTIVE, TRIAL, SUSPENDED, INACTIVE, EXPIRED, PENDING_DELETION, DELETED, MIGRATING, LOCKED';
COMMENT ON COLUMN subscriptions.plan IS 'FREEMIUM, BASIC, PROFESSIONAL, ENTERPRISE, GOVERNMENT, ACADEMIC, CUSTOM, TRIAL';