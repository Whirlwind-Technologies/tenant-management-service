-- V3__add_indexes_and_procedures.sql
-- Add performance indexes and stored procedures for common operations

-- Additional performance indexes for tenants
CREATE INDEX idx_tenant_org_type_status ON tenants(organization_type, status)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_tenant_trial_ends ON tenants(trial_ends_at)
    WHERE status = 'TRIAL' AND is_deleted = FALSE;
CREATE INDEX idx_tenant_expires ON tenants(expires_at)
    WHERE status = 'ACTIVE' AND is_deleted = FALSE;

-- Partial indexes for active tenants
CREATE INDEX idx_active_tenants ON tenants(tenant_code)
    WHERE status IN ('ACTIVE', 'TRIAL') AND is_deleted = FALSE;

-- Additional indexes for subscriptions
CREATE INDEX idx_subscription_active ON subscriptions(tenant_id)
    WHERE subscription_status = 'ACTIVE' AND is_deleted = FALSE;
CREATE INDEX idx_subscription_expiring ON subscriptions(next_renewal_date)
    WHERE auto_renew = TRUE AND is_deleted = FALSE;

-- Tenant context functions for row-level security (needed for V5)
-- Function to set tenant context for RLS
CREATE OR REPLACE FUNCTION set_tenant_context(p_tenant_id UUID)
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_tenant_id', p_tenant_id::TEXT, false);
END;
$$ LANGUAGE plpgsql;

-- Function to get current tenant context (with NULL handling)
CREATE OR REPLACE FUNCTION get_current_tenant_id()
RETURNS UUID AS $$
DECLARE
v_tenant_id TEXT;
BEGIN
    v_tenant_id := current_setting('app.current_tenant_id', true);
    IF v_tenant_id IS NULL OR v_tenant_id = '' THEN
        RETURN NULL;
END IF;
RETURN v_tenant_id::UUID;
EXCEPTION
    WHEN OTHERS THEN
        RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate tenant storage usage
CREATE OR REPLACE FUNCTION calculate_tenant_storage_usage(p_tenant_id UUID)
RETURNS TABLE(
    total_storage_gb DECIMAL,
    storage_quota_gb INTEGER,
    usage_percentage DECIMAL
) AS $$
BEGIN
RETURN QUERY
SELECT
    COALESCE(SUM(ur.quantity), 0) as total_storage_gb,
    t.storage_quota_gb,
    CASE
        WHEN t.storage_quota_gb > 0 THEN
            ROUND((COALESCE(SUM(ur.quantity), 0) / t.storage_quota_gb::DECIMAL) * 100, 2)
        ELSE 0
        END as usage_percentage
FROM tenants t
         LEFT JOIN subscriptions s ON t.id = s.tenant_id
         LEFT JOIN usage_records ur ON s.id = ur.subscription_id
    AND ur.metric_category = 'STORAGE'
    AND ur.usage_date >= CURRENT_DATE - INTERVAL '30 days'
WHERE t.id = p_tenant_id
GROUP BY t.id, t.storage_quota_gb;
END;
$$ LANGUAGE plpgsql;

-- Function to get tenant's active features
CREATE OR REPLACE FUNCTION get_active_features(p_tenant_id UUID)
RETURNS TABLE(
    feature_code VARCHAR,
    feature_name VARCHAR,
    category VARCHAR,
    is_beta BOOLEAN,
    usage_limit INTEGER,
    current_usage INTEGER
) AS $$
BEGIN
RETURN QUERY
SELECT
    ff.feature_code,
    ff.feature_name,
    ff.category,
    ff.is_beta,
    ff.usage_limit,
    ff.current_usage
FROM feature_flags ff
WHERE ff.tenant_id = p_tenant_id
  AND ff.is_enabled = TRUE
  AND ff.is_deleted = FALSE
  AND (ff.enabled_from IS NULL OR ff.enabled_from <= NOW())
  AND (ff.enabled_until IS NULL OR ff.enabled_until >= NOW())
  AND (ff.usage_limit IS NULL OR ff.current_usage < ff.usage_limit)
  AND (NOT ff.requires_approval OR ff.approved_at IS NOT NULL)
ORDER BY ff.category, ff.feature_name;
END;
$$ LANGUAGE plpgsql;

-- Function to check subscription expiration
CREATE OR REPLACE FUNCTION check_subscription_expiration()
RETURNS TABLE(
    tenant_id UUID,
    tenant_name VARCHAR,
    days_until_expiration INTEGER,
    subscription_status VARCHAR
) AS $$
BEGIN
RETURN QUERY
SELECT
    t.id,
    t.name,
    EXTRACT(DAY FROM (s.end_date - CURRENT_TIMESTAMP))::INTEGER as days_until_expiration,
    s.subscription_status
FROM tenants t
         INNER JOIN subscriptions s ON t.id = s.tenant_id
WHERE s.end_date IS NOT NULL
  AND s.end_date <= CURRENT_TIMESTAMP + INTERVAL '30 days'
  AND s.subscription_status = 'ACTIVE'
  AND t.is_deleted = FALSE
  AND s.is_deleted = FALSE
ORDER BY s.end_date;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update tenant status based on subscription
CREATE OR REPLACE FUNCTION update_tenant_status_on_subscription_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.subscription_status = 'ACTIVE' THEN
UPDATE tenants
SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE id = NEW.tenant_id AND status NOT IN ('SUSPENDED', 'LOCKED', 'DELETED');
ELSIF NEW.subscription_status = 'EXPIRED' THEN
UPDATE tenants
SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
WHERE id = NEW.tenant_id;
ELSIF NEW.subscription_status = 'CANCELED' THEN
UPDATE tenants
SET status = 'PENDING_DELETION', updated_at = CURRENT_TIMESTAMP
WHERE id = NEW.tenant_id;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_tenant_status
    AFTER UPDATE OF subscription_status ON subscriptions
    FOR EACH ROW
    WHEN (OLD.subscription_status IS DISTINCT FROM NEW.subscription_status)
    EXECUTE FUNCTION update_tenant_status_on_subscription_change();

-- Trigger to reset feature usage based on frequency
CREATE OR REPLACE FUNCTION reset_feature_usage()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.reset_frequency IS NOT NULL THEN
        CASE NEW.reset_frequency
            WHEN 'DAILY' THEN
                IF NEW.last_reset_at IS NULL OR
                   NEW.last_reset_at < CURRENT_DATE THEN
                    NEW.current_usage := 0;
                    NEW.last_reset_at := CURRENT_TIMESTAMP;
END IF;
WHEN 'WEEKLY' THEN
                IF NEW.last_reset_at IS NULL OR
                   NEW.last_reset_at < CURRENT_DATE - INTERVAL '7 days' THEN
                    NEW.current_usage := 0;
                    NEW.last_reset_at := CURRENT_TIMESTAMP;
END IF;
WHEN 'MONTHLY' THEN
                IF NEW.last_reset_at IS NULL OR
                   EXTRACT(MONTH FROM NEW.last_reset_at) != EXTRACT(MONTH FROM CURRENT_DATE) THEN
                    NEW.current_usage := 0;
                    NEW.last_reset_at := CURRENT_TIMESTAMP;
END IF;
END CASE;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_reset_feature_usage
    BEFORE UPDATE ON feature_flags
    FOR EACH ROW
    WHEN (NEW.current_usage IS NOT NULL)
    EXECUTE FUNCTION reset_feature_usage();

-- View for tenant overview
CREATE OR REPLACE VIEW v_tenant_overview AS
SELECT
    t.id,
    t.tenant_code,
    t.name,
    t.organization_type,
    t.status as tenant_status,
    s.plan as subscription_plan,
    s.subscription_status,
    s.start_date as subscription_start,
    s.end_date as subscription_end,
    s.monthly_price,
    t.created_at,
    t.activated_at,
    COUNT(DISTINCT ff.id) FILTER (WHERE ff.is_enabled = TRUE) as active_features,
    t.max_users,
    t.storage_quota_gb
FROM tenants t
         LEFT JOIN subscriptions s ON t.id = s.tenant_id AND s.is_deleted = FALSE
         LEFT JOIN feature_flags ff ON t.id = ff.tenant_id AND ff.is_deleted = FALSE
WHERE t.is_deleted = FALSE
GROUP BY t.id, s.id;

-- View for subscription metrics
CREATE OR REPLACE VIEW v_subscription_metrics AS
SELECT
    s.id,
    s.tenant_id,
    t.name as tenant_name,
    s.plan,
    s.subscription_status,
    s.monthly_price,
    s.currency,
    COALESCE(
            SUM(ur.amount) FILTER (WHERE ur.usage_date >= DATE_TRUNC('month', CURRENT_DATE)),
            0
    ) as current_month_usage,
    COALESCE(
            SUM(ur.amount) FILTER (WHERE ur.usage_date >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')
                                  AND ur.usage_date < DATE_TRUNC('month', CURRENT_DATE)),
            0
    ) as last_month_usage,
    s.next_renewal_date,
    EXTRACT(DAY FROM (s.next_renewal_date - CURRENT_TIMESTAMP))::INTEGER as days_until_renewal
FROM subscriptions s
         INNER JOIN tenants t ON s.tenant_id = t.id
         LEFT JOIN usage_records ur ON s.id = ur.subscription_id AND ur.is_billable = TRUE
WHERE s.is_deleted = FALSE AND t.is_deleted = FALSE
GROUP BY s.id, t.id, t.name;

-- Index for audit log queries
CREATE INDEX idx_audit_tenant_action_timestamp ON audit_logs(tenant_id, action, timestamp DESC);

-- Function to generate tenant analytics
CREATE OR REPLACE FUNCTION generate_tenant_analytics(
    p_start_date DATE DEFAULT CURRENT_DATE - INTERVAL '30 days',
    p_end_date DATE DEFAULT CURRENT_DATE
)
RETURNS TABLE(
    organization_type VARCHAR,
    total_tenants BIGINT,
    active_tenants BIGINT,
    trial_tenants BIGINT,
    total_revenue DECIMAL,
    avg_revenue_per_tenant DECIMAL
) AS $$
BEGIN
RETURN QUERY
SELECT
    t.organization_type,
    COUNT(DISTINCT t.id) as total_tenants,
    COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'ACTIVE') as active_tenants,
    COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'TRIAL') as trial_tenants,
    COALESCE(SUM(s.monthly_price), 0) as total_revenue,
    COALESCE(AVG(s.monthly_price), 0) as avg_revenue_per_tenant
FROM tenants t
         LEFT JOIN subscriptions s ON t.id = s.tenant_id
WHERE t.created_at BETWEEN p_start_date AND p_end_date
  AND t.is_deleted = FALSE
GROUP BY t.organization_type
ORDER BY total_revenue DESC;
END;
$$ LANGUAGE plpgsql;

-- Add comments
COMMENT ON FUNCTION set_tenant_context IS 'Sets the current tenant context for row-level security policies';
COMMENT ON FUNCTION get_current_tenant_id IS 'Gets the current tenant ID from session context for RLS policies';
COMMENT ON FUNCTION calculate_tenant_storage_usage IS 'Calculate storage usage and quota for a tenant';
COMMENT ON FUNCTION get_active_features IS 'Get all active features for a tenant with usage information';
COMMENT ON FUNCTION check_subscription_expiration IS 'Check for subscriptions expiring in the next 30 days';
COMMENT ON VIEW v_tenant_overview IS 'Consolidated view of tenant information with subscription and feature data';
COMMENT ON VIEW v_subscription_metrics IS 'Subscription metrics including usage and revenue data';