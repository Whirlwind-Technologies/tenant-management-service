-- V4__add_sample_data.sql
-- Sample data for development and testing
-- This migration should only run in dev/test environments

-- Only insert sample data if no tenants exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants LIMIT 1) THEN

        -- Insert sample government agency tenant
        INSERT INTO tenants (
            id, tenant_code, name, display_name, organization_type, status,
            organization_email, organization_phone, organization_website,
            address_line1, city, state_province, postal_code, country,
            is_verified, verified_at, max_users, max_projects, storage_quota_gb,
            api_rate_limit, activated_at, created_at, updated_at
        ) VALUES (
            'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
            'GOV-STATS-001', 'National Statistics Agency', 'NSA Statistics',
            'GOVERNMENT_AGENCY', 'ACTIVE',
            'contact@nsa-stats.gov', '+1-202-555-0100', 'https://nsa-stats.gov',
            '123 Government Plaza', 'Washington', 'DC', '20500', 'US',
            true, NOW(), 500, 100, 10000,
            100000, NOW(), NOW(), NOW()
        );

        -- Insert compliance frameworks for government tenant
INSERT INTO tenant_compliance_frameworks (tenant_id, framework) VALUES
                                                                    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'FISMA'),
                                                                    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'NIST'),
                                                                    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'FedRAMP');

-- Insert subscription for government tenant
INSERT INTO subscriptions (
    id, tenant_id, plan, subscription_status,
    monthly_price, currency, billing_cycle,
    start_date, auto_renew, created_at, updated_at
) VALUES (
             'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'GOVERNMENT', 'ACTIVE',
             2999.99, 'USD', 'ANNUAL',
             NOW(), true, NOW(), NOW()
         );

-- Insert settings for government tenant
INSERT INTO tenant_settings (
    id, tenant_id, mfa_required, session_timeout_minutes,
    data_retention_days, backup_frequency, sso_enabled,
    enable_api_access, enable_advanced_analytics,
    created_at, updated_at
) VALUES (
             'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             true, 15, 2555, 'HOURLY', true,
             true, true, NOW(), NOW()
         );

-- Insert enterprise tenant
INSERT INTO tenants (
    id, tenant_code, name, display_name, organization_type, status,
    organization_email, organization_phone, organization_website,
    address_line1, city, state_province, postal_code, country,
    is_verified, verified_at, max_users, max_projects, storage_quota_gb,
    api_rate_limit, activated_at, created_at, updated_at
) VALUES (
             'a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'CORP-TECH-001', 'TechCorp Analytics', 'TechCorp',
             'CORPORATION', 'ACTIVE',
             'admin@techcorp.com', '+1-415-555-0200', 'https://techcorp.com',
             '456 Tech Boulevard', 'San Francisco', 'CA', '94105', 'US',
             true, NOW(), 200, 50, 5000,
             50000, NOW(), NOW(), NOW()
         );

-- Insert subscription for enterprise tenant
INSERT INTO subscriptions (
    id, tenant_id, plan, subscription_status,
    monthly_price, currency, billing_cycle,
    start_date, auto_renew, created_at, updated_at
) VALUES (
             'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'ENTERPRISE', 'ACTIVE',
             999.99, 'USD', 'MONTHLY',
             NOW(), true, NOW(), NOW()
         );

-- Insert academic tenant
INSERT INTO tenants (
    id, tenant_code, name, display_name, organization_type, status,
    organization_email, organization_phone, organization_website,
    address_line1, city, state_province, postal_code, country,
    is_verified, verified_at, max_users, max_projects, storage_quota_gb,
    api_rate_limit, activated_at, created_at, updated_at
) VALUES (
             'a2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'EDU-UNIV-001', 'State University Research', 'State University',
             'ACADEMIC_INSTITUTION', 'ACTIVE',
             'research@stateuniv.edu', '+1-617-555-0300', 'https://research.stateuniv.edu',
             '789 University Ave', 'Boston', 'MA', '02134', 'US',
             true, NOW(), 100, 200, 5000,
             100000, NOW(), NOW(), NOW()
         );

-- Insert subscription for academic tenant
INSERT INTO subscriptions (
    id, tenant_id, plan, subscription_status,
    monthly_price, currency, billing_cycle,
    discount_percentage, discount_reason,
    start_date, auto_renew, created_at, updated_at
) VALUES (
             'b2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'a2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'ACADEMIC', 'ACTIVE',
             149.99, 'USD', 'ANNUAL',
             50.00, 'Academic Discount',
             NOW(), true, NOW(), NOW()
         );

-- Insert trial tenant (individual)
INSERT INTO tenants (
    id, tenant_code, name, display_name, organization_type, status,
    organization_email, max_users, max_projects, storage_quota_gb,
    api_rate_limit, activated_at, trial_ends_at, created_at, updated_at
) VALUES (
             'a3eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'IND-TRIAL-001', 'John Doe', 'John Doe',
             'INDIVIDUAL', 'TRIAL',
             'john.doe@example.com', 1, 5, 10,
             1000, NOW(), NOW() + INTERVAL '30 days', NOW(), NOW()
         );

-- Insert trial subscription
INSERT INTO subscriptions (
    id, tenant_id, plan, subscription_status,
    monthly_price, currency, billing_cycle,
    start_date, trial_start_date, trial_end_date,
    auto_renew, created_at, updated_at
) VALUES (
             'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'a3eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
             'TRIAL', 'ACTIVE',
             0.00, 'USD', 'MONTHLY',
             NOW(), NOW(), NOW() + INTERVAL '30 days',
             false, NOW(), NOW()
         );

-- Insert feature flags for government tenant
INSERT INTO feature_flags (
    tenant_id, feature_code, feature_name, category,
    is_enabled, is_beta, required_plan, created_at, updated_at
) VALUES
      ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'ADVANCED_ANALYTICS', 'Advanced Analytics', 'ANALYTICS',
       true, false, 'PROFESSIONAL', NOW(), NOW()),
      ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DATA_ENCRYPTION', 'End-to-End Encryption', 'SECURITY',
       true, false, 'GOVERNMENT', NOW(), NOW()),
      ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'API_V2', 'API Version 2', 'INTEGRATION',
       true, true, 'ENTERPRISE', NOW(), NOW()),
      ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'CUSTOM_DASHBOARDS', 'Custom Dashboards', 'UI',
       true, false, 'PROFESSIONAL', NOW(), NOW());

-- Insert feature flags for enterprise tenant
INSERT INTO feature_flags (
    tenant_id, feature_code, feature_name, category,
    is_enabled, is_beta, usage_limit, current_usage,
    reset_frequency, created_at, updated_at
) VALUES
      ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'ADVANCED_ANALYTICS', 'Advanced Analytics', 'ANALYTICS',
       true, false, null, 0, null, NOW(), NOW()),
      ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'API_V2', 'API Version 2', 'INTEGRATION',
       true, true, 10000, 1523, 'DAILY', NOW(), NOW()),
      ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'ML_MODELS', 'Machine Learning Models', 'ANALYTICS',
       true, true, 100, 12, 'MONTHLY', NOW(), NOW());

-- Insert usage records for enterprise tenant
INSERT INTO usage_records (
    subscription_id, usage_date, metric_name, metric_category,
    quantity, unit, rate, amount, is_billable,
    recorded_at, created_at, updated_at
) VALUES
      ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE, 'API_CALLS', 'API',
       45230, 'CALLS', 0.0001, 4.52, true, NOW(), NOW(), NOW()),
      ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE, 'STORAGE', 'STORAGE',
       2350.5, 'GB', 0.10, 235.05, true, NOW(), NOW(), NOW()),
      ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE, 'COMPUTE', 'COMPUTE',
       156.25, 'HOURS', 0.50, 78.13, true, NOW(), NOW(), NOW());

RAISE NOTICE 'Sample data inserted successfully';
ELSE
        RAISE NOTICE 'Tenants already exist, skipping sample data insertion';
END IF;
END $$;