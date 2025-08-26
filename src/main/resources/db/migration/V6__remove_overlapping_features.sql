-- Migration V6: Remove overlapping features with other services
-- This migration removes columns and features that are handled by:
-- - auth-service (authentication)
-- - authz-service (authorization)
-- - api-gateway (rate limiting)
-- - notification-service (notifications)

-- Start transaction
BEGIN;

-- 1. Add new columns for refactored functionality first
ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS fiscal_year_start VARCHAR(10) DEFAULT '01-01',
    ADD COLUMN IF NOT EXISTS theme VARCHAR(50) DEFAULT 'light',
    ADD COLUMN IF NOT EXISTS primary_color VARCHAR(7) DEFAULT '#1976D2',
    ADD COLUMN IF NOT EXISTS secondary_color VARCHAR(7) DEFAULT '#424242',
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS favicon_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS max_export_rows INTEGER DEFAULT 100000,
    ADD COLUMN IF NOT EXISTS business_hours TEXT,
    ADD COLUMN IF NOT EXISTS working_days VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI',
    ADD COLUMN IF NOT EXISTS holiday_calendar VARCHAR(50),
    ADD COLUMN IF NOT EXISTS enable_dashboard_sharing BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS enable_data_collaboration BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS integration_settings JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS notification_preferences JSONB DEFAULT '{}';

-- 2. Migrate data from columns that will be dropped
UPDATE tenant_settings
SET notification_preferences = jsonb_build_object(
        'email', COALESCE(email_notifications_enabled, true),
        'sms', COALESCE(sms_notifications_enabled, false),
        'webhook', webhook_url IS NOT NULL
                               )
WHERE (notification_preferences = '{}' OR notification_preferences IS NULL)
  AND (email_notifications_enabled IS NOT NULL
    OR sms_notifications_enabled IS NOT NULL
    OR webhook_url IS NOT NULL);

-- 3. Remove authentication-related columns from tenant_settings
ALTER TABLE tenant_settings
DROP COLUMN IF EXISTS password_policy,
    DROP COLUMN IF EXISTS session_timeout_minutes,
    DROP COLUMN IF EXISTS mfa_required,
    DROP COLUMN IF EXISTS mfa_type,
    DROP COLUMN IF EXISTS ip_whitelist,
    DROP COLUMN IF EXISTS allowed_domains;

-- 4. Remove authorization-related columns from tenant_settings
ALTER TABLE tenant_settings
DROP COLUMN IF EXISTS sso_enabled,
    DROP COLUMN IF EXISTS sso_provider,
    DROP COLUMN IF EXISTS sso_config;

-- 5. Remove rate limiting columns from tenant_settings
ALTER TABLE tenant_settings
DROP COLUMN IF EXISTS api_rate_limit_override;

-- 6. Remove notification implementation columns from tenant_settings
ALTER TABLE tenant_settings
DROP COLUMN IF EXISTS notification_email,
    DROP COLUMN IF EXISTS billing_email,
    DROP COLUMN IF EXISTS technical_email,
    DROP COLUMN IF EXISTS email_notifications_enabled,
    DROP COLUMN IF EXISTS sms_notifications_enabled,
    DROP COLUMN IF EXISTS webhook_url,
    DROP COLUMN IF EXISTS webhook_secret;

-- 7. Remove security-related feature flags
DELETE FROM feature_flags
WHERE feature_code IN ('MFA', 'SSO', 'BASIC_SECURITY', 'PASSWORD_POLICY');

-- 8. Create index for new JSONB columns
CREATE INDEX IF NOT EXISTS idx_tenant_settings_notification_prefs
    ON tenant_settings USING gin (notification_preferences);
CREATE INDEX IF NOT EXISTS idx_tenant_settings_integration
    ON tenant_settings USING gin (integration_settings);

-- 9. Set default notification preferences for rows that still have empty JSONB
UPDATE tenant_settings
SET notification_preferences = jsonb_build_object(
        'email', true,
        'sms', false,
        'webhook', false
                               )
WHERE notification_preferences = '{}' OR notification_preferences IS NULL;

-- 10. Add comment to document the changes
COMMENT ON TABLE tenant_settings IS 'Tenant configuration and preferences. Auth handled by auth-service, authz by authz-service, rate limiting by api-gateway, notifications by notification-service';

-- Commit transaction
COMMIT;