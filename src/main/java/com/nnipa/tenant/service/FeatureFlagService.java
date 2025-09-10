package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.repository.FeatureFlagRepository;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing tenant feature flags.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final TenantRepository tenantRepository;

    // Feature definitions by plan
    private static final Map<String, Map<String, Boolean>> PLAN_FEATURES = createPlanFeatures();

    private static Map<String, Map<String, Boolean>> createPlanFeatures() {
        Map<String, Map<String, Boolean>> planFeatures = new HashMap<>();

        // ENTERPRISE Plan
        Map<String, Boolean> enterpriseFeatures = new HashMap<>();
        // Data Management Features
        enterpriseFeatures.put("DATA_INGESTION_UNLIMITED", true);
        enterpriseFeatures.put("DATA_CATALOG_ADVANCED", true);
        enterpriseFeatures.put("DATA_STORAGE_UNLIMITED", true);
        enterpriseFeatures.put("DATA_TRANSFORMATION_ADVANCED", true);
        enterpriseFeatures.put("DATA_QUERY_OPTIMIZATION", true);
        enterpriseFeatures.put("DATA_LINEAGE_TRACKING", true);
        enterpriseFeatures.put("DATA_VERSIONING", true);

        // Statistical Computing Features
        enterpriseFeatures.put("STATISTICAL_ENGINE_ADVANCED", true);
        enterpriseFeatures.put("ML_PIPELINE_UNLIMITED", true);
        enterpriseFeatures.put("ANALYSIS_TEMPLATES_CUSTOM", true);
        enterpriseFeatures.put("REPORTING_ADVANCED", true);
        enterpriseFeatures.put("STATISTICAL_MODELING_GPU", true);
        enterpriseFeatures.put("DISTRIBUTED_COMPUTING", true);
        enterpriseFeatures.put("R_PYTHON_INTEGRATION", true);

        // Privacy & Compliance Features
        enterpriseFeatures.put("PRIVACY_ENFORCEMENT_ADVANCED", true);
        enterpriseFeatures.put("AUDIT_LOGS_COMPREHENSIVE", true);
        enterpriseFeatures.put("ENCRYPTION_ADVANCED", true);
        enterpriseFeatures.put("GDPR_COMPLIANCE", true);
        enterpriseFeatures.put("HIPAA_COMPLIANCE", true);
        enterpriseFeatures.put("SOX_COMPLIANCE", true);
        enterpriseFeatures.put("DATA_ANONYMIZATION", true);
        enterpriseFeatures.put("DIFFERENTIAL_PRIVACY", true);

        // Data Exchange & Collaboration Features
        enterpriseFeatures.put("INTEGRATION_UNLIMITED", true);
        enterpriseFeatures.put("WORKSPACE_UNLIMITED", true);
        enterpriseFeatures.put("COLLABORATION_ADVANCED", true);
        enterpriseFeatures.put("CROSS_ORG_SHARING", true);
        enterpriseFeatures.put("API_UNLIMITED", true);
        enterpriseFeatures.put("WEBHOOKS_UNLIMITED", true);
        enterpriseFeatures.put("FEDERATED_LEARNING", true);

        // Visualization & Analytics Features
        enterpriseFeatures.put("VISUALIZATION_ADVANCED", true);
        enterpriseFeatures.put("DASHBOARD_UNLIMITED", true);
        enterpriseFeatures.put("EXPORT_ALL_FORMATS", true);
        enterpriseFeatures.put("CUSTOM_VISUALIZATIONS", true);
        enterpriseFeatures.put("REAL_TIME_DASHBOARDS", true);
        enterpriseFeatures.put("EMBEDDING_WHITELABEL", true);

        // Platform Features
        enterpriseFeatures.put("MONITORING_ADVANCED", true);
        enterpriseFeatures.put("LOGGING_COMPREHENSIVE", true);
        enterpriseFeatures.put("CONFIGURATION_DYNAMIC", true);
        enterpriseFeatures.put("SCHEDULING_UNLIMITED", true);
        enterpriseFeatures.put("NOTIFICATIONS_UNLIMITED", true);
        enterpriseFeatures.put("TENANT_MANAGEMENT_ADVANCED", true);
        enterpriseFeatures.put("USER_MANAGEMENT_UNLIMITED", true);
        enterpriseFeatures.put("SSO_ADVANCED", true);
        enterpriseFeatures.put("RBAC_GRANULAR", true);
        enterpriseFeatures.put("CUSTOM_BRANDING", true);
        enterpriseFeatures.put("PRIORITY_SUPPORT", true);
        enterpriseFeatures.put("SLA_99_9", true);
        enterpriseFeatures.put("DEDICATED_RESOURCES", true);
        enterpriseFeatures.put("ON_PREMISE_DEPLOYMENT", true);

        planFeatures.put("ENTERPRISE", Collections.unmodifiableMap(enterpriseFeatures));

        // PROFESSIONAL Plan
        Map<String, Boolean> professionalFeatures = new HashMap<>();
        // Data Management Features
        professionalFeatures.put("DATA_INGESTION_UNLIMITED", true);
        professionalFeatures.put("DATA_CATALOG_ADVANCED", false);
        professionalFeatures.put("DATA_STORAGE_UNLIMITED", false); // 1TB limit
        professionalFeatures.put("DATA_TRANSFORMATION_ADVANCED", true);
        professionalFeatures.put("DATA_QUERY_OPTIMIZATION", true);
        professionalFeatures.put("DATA_LINEAGE_TRACKING", true);
        professionalFeatures.put("DATA_VERSIONING", false);

        // Statistical Computing Features
        professionalFeatures.put("STATISTICAL_ENGINE_ADVANCED", true);
        professionalFeatures.put("ML_PIPELINE_UNLIMITED", false); // 50 pipelines limit
        professionalFeatures.put("ANALYSIS_TEMPLATES_CUSTOM", false);
        professionalFeatures.put("REPORTING_ADVANCED", true);
        professionalFeatures.put("STATISTICAL_MODELING_GPU", false);
        professionalFeatures.put("DISTRIBUTED_COMPUTING", false);
        professionalFeatures.put("R_PYTHON_INTEGRATION", true);

        // Privacy & Compliance Features
        professionalFeatures.put("PRIVACY_ENFORCEMENT_ADVANCED", true);
        professionalFeatures.put("AUDIT_LOGS_COMPREHENSIVE", false); // 90 days retention
        professionalFeatures.put("ENCRYPTION_ADVANCED", false); // Standard encryption
        professionalFeatures.put("GDPR_COMPLIANCE", true);
        professionalFeatures.put("HIPAA_COMPLIANCE", false);
        professionalFeatures.put("SOX_COMPLIANCE", false);
        professionalFeatures.put("DATA_ANONYMIZATION", true);
        professionalFeatures.put("DIFFERENTIAL_PRIVACY", false);

        // Data Exchange & Collaboration Features
        professionalFeatures.put("INTEGRATION_UNLIMITED", false); // 10 integrations limit
        professionalFeatures.put("WORKSPACE_UNLIMITED", false); // 25 workspaces limit
        professionalFeatures.put("COLLABORATION_ADVANCED", true);
        professionalFeatures.put("CROSS_ORG_SHARING", true);
        professionalFeatures.put("API_UNLIMITED", false); // 10K calls/month
        professionalFeatures.put("WEBHOOKS_UNLIMITED", false); // 100 webhooks limit
        professionalFeatures.put("FEDERATED_LEARNING", false);

        // Visualization & Analytics Features
        professionalFeatures.put("VISUALIZATION_ADVANCED", true);
        professionalFeatures.put("DASHBOARD_UNLIMITED", false); // 50 dashboards limit
        professionalFeatures.put("EXPORT_ALL_FORMATS", true);
        professionalFeatures.put("CUSTOM_VISUALIZATIONS", false);
        professionalFeatures.put("REAL_TIME_DASHBOARDS", true);
        professionalFeatures.put("EMBEDDING_WHITELABEL", false);

        // Platform Features
        professionalFeatures.put("MONITORING_ADVANCED", false); // Basic monitoring
        professionalFeatures.put("LOGGING_COMPREHENSIVE", false); // 30 days retention
        professionalFeatures.put("CONFIGURATION_DYNAMIC", true);
        professionalFeatures.put("SCHEDULING_UNLIMITED", false); // 100 jobs limit
        professionalFeatures.put("NOTIFICATIONS_UNLIMITED", false); // Email/Slack only
        professionalFeatures.put("TENANT_MANAGEMENT_ADVANCED", false);
        professionalFeatures.put("USER_MANAGEMENT_UNLIMITED", false); // 100 users limit
        professionalFeatures.put("SSO_ADVANCED", true); // SAML/OIDC
        professionalFeatures.put("RBAC_GRANULAR", false); // Standard roles
        professionalFeatures.put("CUSTOM_BRANDING", false);
        professionalFeatures.put("PRIORITY_SUPPORT", false); // Business hours
        professionalFeatures.put("SLA_99_9", false); // 99% SLA
        professionalFeatures.put("DEDICATED_RESOURCES", false);
        professionalFeatures.put("ON_PREMISE_DEPLOYMENT", false);

        planFeatures.put("PROFESSIONAL", Collections.unmodifiableMap(professionalFeatures));

        // STANDARD Plan
        Map<String, Boolean> standardFeatures = new HashMap<>();
        // Data Management Features
        standardFeatures.put("DATA_INGESTION_UNLIMITED", false); // 100GB/month limit
        standardFeatures.put("DATA_CATALOG_ADVANCED", false);
        standardFeatures.put("DATA_STORAGE_UNLIMITED", false); // 500GB limit
        standardFeatures.put("DATA_TRANSFORMATION_ADVANCED", false);
        standardFeatures.put("DATA_QUERY_OPTIMIZATION", false);
        standardFeatures.put("DATA_LINEAGE_TRACKING", false);
        standardFeatures.put("DATA_VERSIONING", false);

        // Statistical Computing Features
        standardFeatures.put("STATISTICAL_ENGINE_ADVANCED", false); // Basic stats only
        standardFeatures.put("ML_PIPELINE_UNLIMITED", false); // 10 pipelines limit
        standardFeatures.put("ANALYSIS_TEMPLATES_CUSTOM", false);
        standardFeatures.put("REPORTING_ADVANCED", false); // Standard reports
        standardFeatures.put("STATISTICAL_MODELING_GPU", false);
        standardFeatures.put("DISTRIBUTED_COMPUTING", false);
        standardFeatures.put("R_PYTHON_INTEGRATION", false);

        // Privacy & Compliance Features
        standardFeatures.put("PRIVACY_ENFORCEMENT_ADVANCED", false); // Basic privacy
        standardFeatures.put("AUDIT_LOGS_COMPREHENSIVE", false); // 30 days retention
        standardFeatures.put("ENCRYPTION_ADVANCED", false); // Standard encryption
        standardFeatures.put("GDPR_COMPLIANCE", true);
        standardFeatures.put("HIPAA_COMPLIANCE", false);
        standardFeatures.put("SOX_COMPLIANCE", false);
        standardFeatures.put("DATA_ANONYMIZATION", false);
        standardFeatures.put("DIFFERENTIAL_PRIVACY", false);

        // Data Exchange & Collaboration Features
        standardFeatures.put("INTEGRATION_UNLIMITED", false); // 5 integrations limit
        standardFeatures.put("WORKSPACE_UNLIMITED", false); // 10 workspaces limit
        standardFeatures.put("COLLABORATION_ADVANCED", false); // Basic sharing
        standardFeatures.put("CROSS_ORG_SHARING", false);
        standardFeatures.put("API_UNLIMITED", false); // 1K calls/month
        standardFeatures.put("WEBHOOKS_UNLIMITED", false); // 10 webhooks limit
        standardFeatures.put("FEDERATED_LEARNING", false);

        // Visualization & Analytics Features
        standardFeatures.put("VISUALIZATION_ADVANCED", false); // Standard charts
        standardFeatures.put("DASHBOARD_UNLIMITED", false); // 10 dashboards limit
        standardFeatures.put("EXPORT_ALL_FORMATS", false); // PDF/CSV only
        standardFeatures.put("CUSTOM_VISUALIZATIONS", false);
        standardFeatures.put("REAL_TIME_DASHBOARDS", false);
        standardFeatures.put("EMBEDDING_WHITELABEL", false);

        // Platform Features
        standardFeatures.put("MONITORING_ADVANCED", false); // Basic monitoring
        standardFeatures.put("LOGGING_COMPREHENSIVE", false); // 7 days retention
        standardFeatures.put("CONFIGURATION_DYNAMIC", false);
        standardFeatures.put("SCHEDULING_UNLIMITED", false); // 20 jobs limit
        standardFeatures.put("NOTIFICATIONS_UNLIMITED", false); // Email only
        standardFeatures.put("TENANT_MANAGEMENT_ADVANCED", false);
        standardFeatures.put("USER_MANAGEMENT_UNLIMITED", false); // 25 users limit
        standardFeatures.put("SSO_ADVANCED", false); // Basic SSO
        standardFeatures.put("RBAC_GRANULAR", false); // Predefined roles
        standardFeatures.put("CUSTOM_BRANDING", false);
        standardFeatures.put("PRIORITY_SUPPORT", false); // Community support
        standardFeatures.put("SLA_99_9", false); // Best effort
        standardFeatures.put("DEDICATED_RESOURCES", false);
        standardFeatures.put("ON_PREMISE_DEPLOYMENT", false);

        planFeatures.put("STANDARD", Collections.unmodifiableMap(standardFeatures));

        // STARTER Plan
        Map<String, Boolean> starterFeatures = new HashMap<>();
        // Data Management Features
        starterFeatures.put("DATA_INGESTION_UNLIMITED", false); // 10GB/month limit
        starterFeatures.put("DATA_CATALOG_ADVANCED", false);
        starterFeatures.put("DATA_STORAGE_UNLIMITED", false); // 50GB limit
        starterFeatures.put("DATA_TRANSFORMATION_ADVANCED", false);
        starterFeatures.put("DATA_QUERY_OPTIMIZATION", false);
        starterFeatures.put("DATA_LINEAGE_TRACKING", false);
        starterFeatures.put("DATA_VERSIONING", false);

        // Statistical Computing Features
        starterFeatures.put("STATISTICAL_ENGINE_ADVANCED", false); // Basic stats only
        starterFeatures.put("ML_PIPELINE_UNLIMITED", false); // 3 pipelines limit
        starterFeatures.put("ANALYSIS_TEMPLATES_CUSTOM", false);
        starterFeatures.put("REPORTING_ADVANCED", false); // Basic reports
        starterFeatures.put("STATISTICAL_MODELING_GPU", false);
        starterFeatures.put("DISTRIBUTED_COMPUTING", false);
        starterFeatures.put("R_PYTHON_INTEGRATION", false);

        // Privacy & Compliance Features
        starterFeatures.put("PRIVACY_ENFORCEMENT_ADVANCED", false); // Basic privacy
        starterFeatures.put("AUDIT_LOGS_COMPREHENSIVE", false); // 7 days retention
        starterFeatures.put("ENCRYPTION_ADVANCED", false); // Basic encryption
        starterFeatures.put("GDPR_COMPLIANCE", false);
        starterFeatures.put("HIPAA_COMPLIANCE", false);
        starterFeatures.put("SOX_COMPLIANCE", false);
        starterFeatures.put("DATA_ANONYMIZATION", false);
        starterFeatures.put("DIFFERENTIAL_PRIVACY", false);

        // Data Exchange & Collaboration Features
        starterFeatures.put("INTEGRATION_UNLIMITED", false); // 2 integrations limit
        starterFeatures.put("WORKSPACE_UNLIMITED", false); // 3 workspaces limit
        starterFeatures.put("COLLABORATION_ADVANCED", false); // Basic sharing
        starterFeatures.put("CROSS_ORG_SHARING", false);
        starterFeatures.put("API_UNLIMITED", false); // 100 calls/month
        starterFeatures.put("WEBHOOKS_UNLIMITED", false); // 3 webhooks limit
        starterFeatures.put("FEDERATED_LEARNING", false);

        // Visualization & Analytics Features
        starterFeatures.put("VISUALIZATION_ADVANCED", false); // Basic charts
        starterFeatures.put("DASHBOARD_UNLIMITED", false); // 3 dashboards limit
        starterFeatures.put("EXPORT_ALL_FORMATS", false); // CSV only
        starterFeatures.put("CUSTOM_VISUALIZATIONS", false);
        starterFeatures.put("REAL_TIME_DASHBOARDS", false);
        starterFeatures.put("EMBEDDING_WHITELABEL", false);

        // Platform Features
        starterFeatures.put("MONITORING_ADVANCED", false); // Basic monitoring
        starterFeatures.put("LOGGING_COMPREHENSIVE", false); // 3 days retention
        starterFeatures.put("CONFIGURATION_DYNAMIC", false);
        starterFeatures.put("SCHEDULING_UNLIMITED", false); // 5 jobs limit
        starterFeatures.put("NOTIFICATIONS_UNLIMITED", false); // Email only
        starterFeatures.put("TENANT_MANAGEMENT_ADVANCED", false);
        starterFeatures.put("USER_MANAGEMENT_UNLIMITED", false); // 5 users limit
        starterFeatures.put("SSO_ADVANCED", false); // No SSO
        starterFeatures.put("RBAC_GRANULAR", false); // Basic roles
        starterFeatures.put("CUSTOM_BRANDING", false);
        starterFeatures.put("PRIORITY_SUPPORT", false); // Community support
        starterFeatures.put("SLA_99_9", false); // Best effort
        starterFeatures.put("DEDICATED_RESOURCES", false);
        starterFeatures.put("ON_PREMISE_DEPLOYMENT", false);

        planFeatures.put("STARTER", Collections.unmodifiableMap(starterFeatures));

        // TRIAL Plan
        Map<String, Boolean> trialFeatures = new HashMap<>();
        // Data Management Features - Very Limited
        trialFeatures.put("DATA_INGESTION_UNLIMITED", false); // 1GB/month limit
        trialFeatures.put("DATA_CATALOG_ADVANCED", false);
        trialFeatures.put("DATA_STORAGE_UNLIMITED", false); // 5GB limit
        trialFeatures.put("DATA_TRANSFORMATION_ADVANCED", false);
        trialFeatures.put("DATA_QUERY_OPTIMIZATION", false);
        trialFeatures.put("DATA_LINEAGE_TRACKING", false);
        trialFeatures.put("DATA_VERSIONING", false);

        // Statistical Computing Features - Basic Only
        trialFeatures.put("STATISTICAL_ENGINE_ADVANCED", false); // Descriptive stats only
        trialFeatures.put("ML_PIPELINE_UNLIMITED", false); // 1 pipeline limit
        trialFeatures.put("ANALYSIS_TEMPLATES_CUSTOM", false);
        trialFeatures.put("REPORTING_ADVANCED", false); // Basic reports
        trialFeatures.put("STATISTICAL_MODELING_GPU", false);
        trialFeatures.put("DISTRIBUTED_COMPUTING", false);
        trialFeatures.put("R_PYTHON_INTEGRATION", false);

        // Privacy & Compliance Features - Minimal
        trialFeatures.put("PRIVACY_ENFORCEMENT_ADVANCED", false);
        trialFeatures.put("AUDIT_LOGS_COMPREHENSIVE", false); // 3 days retention
        trialFeatures.put("ENCRYPTION_ADVANCED", false); // Basic encryption
        trialFeatures.put("GDPR_COMPLIANCE", false);
        trialFeatures.put("HIPAA_COMPLIANCE", false);
        trialFeatures.put("SOX_COMPLIANCE", false);
        trialFeatures.put("DATA_ANONYMIZATION", false);
        trialFeatures.put("DIFFERENTIAL_PRIVACY", false);

        // Data Exchange & Collaboration Features - Very Limited
        trialFeatures.put("INTEGRATION_UNLIMITED", false); // 1 integration limit
        trialFeatures.put("WORKSPACE_UNLIMITED", false); // 1 workspace limit
        trialFeatures.put("COLLABORATION_ADVANCED", false); // View only sharing
        trialFeatures.put("CROSS_ORG_SHARING", false);
        trialFeatures.put("API_UNLIMITED", false); // 50 calls/month
        trialFeatures.put("WEBHOOKS_UNLIMITED", false); // 1 webhook limit
        trialFeatures.put("FEDERATED_LEARNING", false);

        // Visualization & Analytics Features - Basic Only
        trialFeatures.put("VISUALIZATION_ADVANCED", false); // Basic charts
        trialFeatures.put("DASHBOARD_UNLIMITED", false); // 1 dashboard limit
        trialFeatures.put("EXPORT_ALL_FORMATS", false); // View only
        trialFeatures.put("CUSTOM_VISUALIZATIONS", false);
        trialFeatures.put("REAL_TIME_DASHBOARDS", false);
        trialFeatures.put("EMBEDDING_WHITELABEL", false);

        // Platform Features - Minimal
        trialFeatures.put("MONITORING_ADVANCED", false); // Basic monitoring
        trialFeatures.put("LOGGING_COMPREHENSIVE", false); // 1 day retention
        trialFeatures.put("CONFIGURATION_DYNAMIC", false);
        trialFeatures.put("SCHEDULING_UNLIMITED", false); // 2 jobs limit
        trialFeatures.put("NOTIFICATIONS_UNLIMITED", false); // Email only
        trialFeatures.put("TENANT_MANAGEMENT_ADVANCED", false);
        trialFeatures.put("USER_MANAGEMENT_UNLIMITED", false); // 2 users limit
        trialFeatures.put("SSO_ADVANCED", false); // No SSO
        trialFeatures.put("RBAC_GRANULAR", false); // Admin/User only
        trialFeatures.put("CUSTOM_BRANDING", false);
        trialFeatures.put("PRIORITY_SUPPORT", false); // No support
        trialFeatures.put("SLA_99_9", false); // Best effort
        trialFeatures.put("DEDICATED_RESOURCES", false);
        trialFeatures.put("ON_PREMISE_DEPLOYMENT", false);

        planFeatures.put("TRIAL", Collections.unmodifiableMap(trialFeatures));

        return Collections.unmodifiableMap(planFeatures);
    }

    /**
     * Initialize feature flags for a new tenant.
     */
    @Transactional
    public Map<String, Boolean> initializeFeatureFlags(UUID tenantId, String subscriptionPlan) {
        log.info("Initializing feature flags for tenant: {} with plan: {}", tenantId, subscriptionPlan);

        Map<String, Boolean> planFeatures = PLAN_FEATURES.getOrDefault(subscriptionPlan,
                PLAN_FEATURES.get("TRIAL"));
        Map<String, Boolean> enabledFeatures = new HashMap<>();

        for (Map.Entry<String, Boolean> entry : planFeatures.entrySet()) {
            FeatureFlag flag = FeatureFlag.builder()
                    .tenantId(tenantId)
                    .featureName(entry.getKey())
                    .enabled(entry.getValue())
                    .source("PLAN")
                    .createdAt(Instant.now())
                    .build();

            featureFlagRepository.save(flag);
            enabledFeatures.put(entry.getKey(), entry.getValue());
        }

        log.info("Initialized {} feature flags for tenant: {}", enabledFeatures.size(), tenantId);
        return enabledFeatures;
    }

    /**
     * Update feature flags when subscription plan changes.
     */
    @Transactional
    @CacheEvict(value = "feature-flags", key = "#tenantId")
    public Map<String, Boolean> updateFeatureFlagsForPlan(UUID tenantId, String newPlan) {
        log.info("Updating feature flags for tenant: {} to plan: {}", tenantId, newPlan);

        Map<String, Boolean> newPlanFeatures = PLAN_FEATURES.getOrDefault(newPlan,
                PLAN_FEATURES.get("TRIAL"));
        List<FeatureFlag> existingFlags = featureFlagRepository.findByTenantId(tenantId);
        Map<String, Boolean> updatedFeatures = new HashMap<>();

        // Update existing flags
        for (FeatureFlag flag : existingFlags) {
            if (newPlanFeatures.containsKey(flag.getFeatureName())) {
                boolean newValue = newPlanFeatures.get(flag.getFeatureName());

                // Only update if the flag is managed by plan (not custom override)
                if ("PLAN".equals(flag.getSource())) {
                    flag.setEnabled(newValue);
                    flag.setUpdatedAt(Instant.now());
                    featureFlagRepository.save(flag);
                }

                updatedFeatures.put(flag.getFeatureName(), flag.getEnabled());
            }
        }

        // Add any new features from the plan
        Set<String> existingFeatureNames = new HashSet<>();
        existingFlags.forEach(f -> existingFeatureNames.add(f.getFeatureName()));

        for (Map.Entry<String, Boolean> entry : newPlanFeatures.entrySet()) {
            if (!existingFeatureNames.contains(entry.getKey())) {
                FeatureFlag flag = FeatureFlag.builder()
                        .tenantId(tenantId)
                        .featureName(entry.getKey())
                        .enabled(entry.getValue())
                        .source("PLAN")
                        .createdAt(Instant.now())
                        .build();

                featureFlagRepository.save(flag);
                updatedFeatures.put(entry.getKey(), entry.getValue());
            }
        }

        log.info("Updated {} feature flags for tenant: {}", updatedFeatures.size(), tenantId);
        return updatedFeatures;
    }

    /**
     * Get all feature flags for a tenant.
     */
    @Cacheable(value = "feature-flags", key = "#tenantId")
    public Map<String, Boolean> getFeatureFlags(UUID tenantId) {
        List<FeatureFlag> flags = featureFlagRepository.findByTenantId(tenantId);
        Map<String, Boolean> featureMap = new HashMap<>();

        for (FeatureFlag flag : flags) {
            featureMap.put(flag.getFeatureName(), flag.getEnabled());
        }

        return featureMap;
    }

    /**
     * Check if a specific feature is enabled for a tenant.
     */
    @Cacheable(value = "feature-flag", key = "#tenantId + ':' + #featureName")
    public boolean isFeatureEnabled(UUID tenantId, String featureName) {
        Optional<FeatureFlag> flag = featureFlagRepository.findByTenantIdAndFeatureName(tenantId, featureName);
        return flag.map(FeatureFlag::getEnabled).orElse(false);
    }

    /**
     * Override a feature flag for a specific tenant.
     */
    @Transactional
    @CacheEvict(value = {"feature-flags", "feature-flag"}, key = "#tenantId")
    public FeatureFlag overrideFeatureFlag(UUID tenantId, String featureName, boolean enabled, String reason) {
        log.info("Overriding feature {} for tenant: {} to: {}", featureName, tenantId, enabled);

        FeatureFlag flag = featureFlagRepository.findByTenantIdAndFeatureName(tenantId, featureName)
                .orElse(FeatureFlag.builder()
                        .tenantId(tenantId)
                        .featureName(featureName)
                        .createdAt(Instant.now())
                        .build());

        flag.setEnabled(enabled);
        flag.setSource("OVERRIDE");
        flag.setOverrideReason(reason);
        flag.setUpdatedAt(Instant.now());

        flag = featureFlagRepository.save(flag);

        log.info("Feature {} overridden for tenant: {}", featureName, tenantId);
        return flag;
    }

    /**
     * Reset a feature flag to plan default.
     */
    @Transactional
    @CacheEvict(value = {"feature-flags", "feature-flag"}, key = "#tenantId")
    public FeatureFlag resetFeatureFlag(UUID tenantId, String featureName) {
        log.info("Resetting feature {} for tenant: {}", featureName, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        String plan = tenant.getSubscriptionPlan();
        Map<String, Boolean> planFeatures = PLAN_FEATURES.getOrDefault(plan, PLAN_FEATURES.get("TRIAL"));

        FeatureFlag flag = featureFlagRepository.findByTenantIdAndFeatureName(tenantId, featureName)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + featureName));

        flag.setEnabled(planFeatures.getOrDefault(featureName, false));
        flag.setSource("PLAN");
        flag.setOverrideReason(null);
        flag.setUpdatedAt(Instant.now());

        flag = featureFlagRepository.save(flag);

        log.info("Feature {} reset to plan default for tenant: {}", featureName, tenantId);
        return flag;
    }

    /**
     * Get feature usage statistics for a tenant.
     */
    public Map<String, Object> getFeatureUsageStats(UUID tenantId) {
        Map<String, Boolean> flags = getFeatureFlags(tenantId);

        long enabledCount = flags.values().stream().filter(Boolean::booleanValue).count();
        long totalCount = flags.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFeatures", totalCount);
        stats.put("enabledFeatures", enabledCount);
        stats.put("disabledFeatures", totalCount - enabledCount);
        stats.put("enabledPercentage", totalCount > 0 ? (enabledCount * 100.0 / totalCount) : 0);
        stats.put("features", flags);

        return stats;
    }
}