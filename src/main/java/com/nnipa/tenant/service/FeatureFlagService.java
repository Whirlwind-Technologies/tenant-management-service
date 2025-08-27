package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing feature flags per tenant.
 * Controls feature access based on subscription tier, organization type, and custom rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    // Feature definitions
    private static final Map<String, FeatureDefinition> FEATURE_CATALOG = initializeFeatureCatalog();

    /**
     * Initializes default features for a tenant based on their organization type and plan.
     */
    @Transactional
    public List<FeatureFlag> initializeTenantFeatures(Tenant tenant) {
        log.info("Initializing features for tenant: {}", tenant.getName());

        List<FeatureFlag> features = new ArrayList<>();
        SubscriptionPlan plan = tenant.getSubscription() != null ?
                tenant.getSubscription().getPlan() : SubscriptionPlan.FREEMIUM;

        for (FeatureDefinition definition : FEATURE_CATALOG.values()) {
            if (shouldEnableFeature(definition, tenant.getOrganizationType(), plan)) {
                FeatureFlag feature = createFeatureFlag(tenant, definition, plan);
                features.add(feature);
            }
        }

        features = featureFlagRepository.saveAll(features);
        log.info("Initialized {} features for tenant: {}", features.size(), tenant.getName());

        return features;
    }

    /**
     * Gets all available features from the feature catalog.
     * This returns template features that can be enabled for tenants.
     */
    public List<FeatureFlag> getAllFeatures() {
        log.debug("Fetching all available features from catalog");

        List<FeatureFlag> allFeatures = new ArrayList<>();

        for (FeatureDefinition definition : FEATURE_CATALOG.values()) {
            FeatureFlag templateFeature = FeatureFlag.builder()
                    .featureCode(definition.code())
                    .featureName(definition.name())
                    .category(definition.category())
                    .isBeta(definition.isBeta())
                    .isEnabled(false) // Template features are disabled by default
                    .requiredPlan(definition.minimumPlans().isEmpty() ? null :
                            definition.minimumPlans().iterator().next().name())
                    .build();

            // Set default usage limits for template
            setDefaultUsageLimits(templateFeature, definition);

            allFeatures.add(templateFeature);
        }

        log.debug("Retrieved {} features from catalog", allFeatures.size());
        return allFeatures;
    }

    /**
     * Sets default usage limits for template features based on feature definition.
     */
    private void setDefaultUsageLimits(FeatureFlag feature, FeatureDefinition definition) {
        // Set default limits that would apply to the most restrictive plan that supports this feature
        SubscriptionPlan mostRestrictivePlan = definition.minimumPlans().isEmpty() ?
                SubscriptionPlan.FREEMIUM :
                definition.minimumPlans().stream()
                        .min((p1, p2) -> Integer.compare((int) p1.getSlaUptime(), (int) p2.getSlaUptime()))
                        .orElse(SubscriptionPlan.FREEMIUM);

        setUsageLimits(feature, mostRestrictivePlan);
    }

    /**
     * Enables a feature for a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id")
    public FeatureFlag enableFeature(Tenant tenant, String featureCode) {
        log.info("Enabling feature {} for tenant: {}", featureCode, tenant.getName());

        FeatureFlag feature = featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .orElseGet(() -> createFeatureFlag(tenant, FEATURE_CATALOG.get(featureCode),
                        tenant.getSubscription().getPlan()));

        // Check if tenant is eligible for this feature
        if (!isEligibleForFeature(tenant, featureCode)) {
            throw new FeatureNotAvailableException(
                    String.format("Feature %s is not available for tenant's plan", featureCode)
            );
        }

        feature.enable();
        feature = featureFlagRepository.save(feature);

        log.info("Feature {} enabled for tenant: {}", featureCode, tenant.getName());
        return feature;
    }

    /**
     * Checks if a feature is enabled for a tenant.
     */
    @Cacheable(value = "tenant-features", key = "#tenant.id + ':' + #featureCode")
    public boolean isFeatureEnabled(Tenant tenant, String featureCode) {
        return featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .map(FeatureFlag::isAccessible)
                .orElse(false);
    }

    /**
     * Gets all features for a tenant.
     */
    @Cacheable(value = "tenant-features", key = "#tenant.id")
    public List<FeatureFlag> getTenantFeatures(Tenant tenant) {
        return featureFlagRepository.findByTenant(tenant);
    }

    /**
     * Gets enabled features for a tenant.
     */
    public List<FeatureFlag> getEnabledFeatures(Tenant tenant) {
        return featureFlagRepository.findByTenantAndIsEnabled(tenant, true);
    }

    /**
     * Grants trial access to a premium feature.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id")
    public FeatureFlag grantTrialAccess(Tenant tenant, String featureCode, int trialDays) {
        log.info("Granting {} day trial for feature {} to tenant: {}",
                trialDays, featureCode, tenant.getName());

        FeatureFlag feature = featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .orElseGet(() -> createFeatureFlag(tenant, FEATURE_CATALOG.get(featureCode),
                        tenant.getSubscription().getPlan()));

        feature.setTrialEnabled(true);
        feature.setTrialDays(trialDays);
        feature.setEnabledFrom(Instant.now());
        feature.setEnabledUntil(Instant.now().plusSeconds(trialDays * 24L * 60 * 60));
        feature.enable();

        feature = featureFlagRepository.save(feature);

        log.info("Trial access granted for feature: {}", featureCode);
        return feature;
    }

    /**
     * Updates feature usage.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id + ':' + #featureCode")
    public void recordFeatureUsage(Tenant tenant, String featureCode) {
        featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .ifPresent(feature -> {
                    feature.incrementUsage();
                    featureFlagRepository.save(feature);
                });
    }


    /**
     * Approves a feature that requires approval.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id")
    public FeatureFlag approveFeature(Tenant tenant, String featureCode, String approvedBy, String notes) {
        log.info("Approving feature {} for tenant: {}", featureCode, tenant.getName());

        FeatureFlag feature = featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .orElseThrow(() -> new FeatureNotFoundException("Feature not found: " + featureCode));

        if (!feature.getRequiresApproval()) {
            throw new IllegalStateException("Feature does not require approval");
        }

        feature.setApprovedBy(approvedBy);
        feature.setApprovedAt(Instant.now());
        feature.setApprovalNotes(notes);
        feature.enable();

        feature = featureFlagRepository.save(feature);

        log.info("Feature {} approved for tenant: {}", featureCode, tenant.getName());
        return feature;
    }


    /**
     * Disables a feature for a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id")
    public FeatureFlag disableFeature(Tenant tenant, String featureCode) {
        log.info("Disabling feature {} for tenant: {}", featureCode, tenant.getName());

        FeatureFlag feature = featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .orElseThrow(() -> new FeatureNotFoundException("Feature not found: " + featureCode));

        feature.disable();
        feature = featureFlagRepository.save(feature);

        log.info("Feature {} disabled for tenant: {}", featureCode, tenant.getName());
        return feature;
    }

    /**
     * Sets A/B test group for a feature.
     */
    @Transactional
    @CacheEvict(value = "tenant-features", key = "#tenant.id")
    public FeatureFlag setABTestGroup(Tenant tenant, String featureCode, String group, int rolloutPercentage) {
        log.info("Setting A/B test group {} for feature {} ({}% rollout)",
                group, featureCode, rolloutPercentage);

        FeatureFlag feature = featureFlagRepository.findByTenantAndFeatureCode(tenant, featureCode)
                .orElseThrow(() -> new FeatureNotFoundException("Feature not found: " + featureCode));

        feature.setRolloutGroup(group);
        feature.setRolloutPercentage(rolloutPercentage);

        // Enable based on rollout percentage
        if (shouldEnableBasedOnRollout(tenant, rolloutPercentage)) {
            feature.enable();
        }

        feature = featureFlagRepository.save(feature);

        log.info("A/B test group set for feature: {}", featureCode);
        return feature;
    }

    /**
     * Scheduled task to reset feature usage counters.
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    @Transactional
    public void resetDailyUsageCounters() {
        log.info("Resetting daily feature usage counters");

        List<FeatureFlag> dailyFeatures = featureFlagRepository.findByResetFrequency("DAILY");
        for (FeatureFlag feature : dailyFeatures) {
            feature.resetUsage();
            featureFlagRepository.save(feature);
        }

        log.info("Reset {} daily feature counters", dailyFeatures.size());
    }

    /**
     * Scheduled task to check expired feature trials.
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    @Transactional
    public void checkExpiredTrials() {
        log.info("Checking for expired feature trials");

        Instant now = Instant.now();
        List<FeatureFlag> expiredTrials = featureFlagRepository.findExpiredTrials(now);

        for (FeatureFlag feature : expiredTrials) {
            feature.disable();
            feature.setTrialEnabled(false);
            featureFlagRepository.save(feature);
            log.info("Disabled expired trial feature {} for tenant {}",
                    feature.getFeatureCode(), feature.getTenant().getName());
        }

        log.info("Processed {} expired trials", expiredTrials.size());
    }

    /**
     * Enables a feature for a tenant (wrapper method).
     */
    @Transactional
    public FeatureFlag enableFeatureForTenant(Tenant tenant, String featureCode) {
        return enableFeature(tenant, featureCode);
    }

    /**
     * Disables a feature for a tenant (wrapper method).
     */
    @Transactional
    public FeatureFlag disableFeatureForTenant(Tenant tenant, String featureCode) {
        return disableFeature(tenant, featureCode);
    }

    /**
     * Checks if a feature is enabled for a tenant (wrapper method).
     */
    public boolean isFeatureEnabledForTenant(Tenant tenant, String featureCode) {
        return isFeatureEnabled(tenant, featureCode);
    }

    // Helper methods

    private static Map<String, FeatureDefinition> initializeFeatureCatalog() {
        Map<String, FeatureDefinition> catalog = new HashMap<>();

        // Analytics features
        catalog.put("BASIC_ANALYTICS", new FeatureDefinition(
                "BASIC_ANALYTICS", "Basic Analytics", "ANALYTICS",
                Set.of(SubscriptionPlan.FREEMIUM), Set.of(), false
        ));

        catalog.put("ADVANCED_ANALYTICS", new FeatureDefinition(
                "ADVANCED_ANALYTICS", "Advanced Analytics", "ANALYTICS",
                Set.of(SubscriptionPlan.PROFESSIONAL, SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT),
                Set.of(OrganizationType.CORPORATION, OrganizationType.GOVERNMENT_AGENCY), true
        ));

        catalog.put("PREDICTIVE_ANALYTICS", new FeatureDefinition(
                "PREDICTIVE_ANALYTICS", "Predictive Analytics", "ANALYTICS",
                Set.of(SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT),
                Set.of(OrganizationType.FINANCIAL_INSTITUTION, OrganizationType.GOVERNMENT_AGENCY), true
        ));

        catalog.put("DATA_ENCRYPTION", new FeatureDefinition(
                "DATA_ENCRYPTION", "End-to-End Data Encryption", "SECURITY",
                Set.of(SubscriptionPlan.PROFESSIONAL),
                Set.of(OrganizationType.HEALTHCARE, OrganizationType.FINANCIAL_INSTITUTION), true
        ));

        // Integration features
        catalog.put("API_ACCESS", new FeatureDefinition(
                "API_ACCESS", "API Access", "INTEGRATION",
                Set.of(SubscriptionPlan.BASIC), Set.of(), false
        ));

        catalog.put("WEBHOOKS", new FeatureDefinition(
                "WEBHOOKS", "Webhooks", "INTEGRATION",
                Set.of(SubscriptionPlan.PROFESSIONAL), Set.of(), false
        ));

        catalog.put("CUSTOM_INTEGRATIONS", new FeatureDefinition(
                "CUSTOM_INTEGRATIONS", "Custom Integrations", "INTEGRATION",
                Set.of(SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT),
                Set.of(OrganizationType.CORPORATION, OrganizationType.GOVERNMENT_AGENCY), true
        ));

        // UI features
        catalog.put("CUSTOM_DASHBOARDS", new FeatureDefinition(
                "CUSTOM_DASHBOARDS", "Custom Dashboards", "UI",
                Set.of(SubscriptionPlan.PROFESSIONAL), Set.of(), false
        ));

        catalog.put("WHITE_LABEL", new FeatureDefinition(
                "WHITE_LABEL", "White Label Branding", "UI",
                Set.of(SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT),
                Set.of(OrganizationType.CORPORATION, OrganizationType.GOVERNMENT_AGENCY), false
        ));

        // Data features
        catalog.put("DATA_EXPORT", new FeatureDefinition(
                "DATA_EXPORT", "Data Export", "DATA",
                Set.of(SubscriptionPlan.BASIC), Set.of(), false
        ));

        catalog.put("DATA_SHARING", new FeatureDefinition(
                "DATA_SHARING", "Cross-Tenant Data Sharing", "DATA",
                Set.of(SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT, SubscriptionPlan.ACADEMIC),
                Set.of(OrganizationType.GOVERNMENT_AGENCY, OrganizationType.ACADEMIC_INSTITUTION), true
        ));

        catalog.put("AUTOMATED_BACKUPS", new FeatureDefinition(
                "AUTOMATED_BACKUPS", "Automated Backups", "DATA",
                Set.of(SubscriptionPlan.PROFESSIONAL), Set.of(), false
        ));

        catalog.put("COMPLIANCE_REPORTS", new FeatureDefinition(
                "COMPLIANCE_REPORTS", "Compliance Reports", "DATA",
                Set.of(SubscriptionPlan.ENTERPRISE, SubscriptionPlan.GOVERNMENT),
                Set.of(OrganizationType.GOVERNMENT_AGENCY, OrganizationType.FINANCIAL_INSTITUTION, OrganizationType.HEALTHCARE), false
        ));

        return catalog;
    }

    private boolean shouldEnableFeature(FeatureDefinition definition, OrganizationType orgType, SubscriptionPlan plan) {
        // Check if plan supports the feature
        if (!definition.minimumPlans.isEmpty()) {
            boolean planSupported = definition.minimumPlans.stream()
                    .anyMatch(minPlan -> plan == minPlan || plan.getSlaUptime() >= minPlan.getSlaUptime());
            if (!planSupported) return false;
        }

        // Check if organization type is suitable
        if (!definition.suitableOrgTypes.isEmpty() && !definition.suitableOrgTypes.contains(orgType)) {
            return false;
        }

        // Enable by default if beta and org type is suitable for beta
        if (definition.isBeta) {
            return orgType == OrganizationType.CORPORATION ||
                    orgType == OrganizationType.STARTUP ||
                    orgType == OrganizationType.INDIVIDUAL;
        }

        return true;
    }

    private FeatureFlag createFeatureFlag(Tenant tenant, FeatureDefinition definition, SubscriptionPlan plan) {
        if (definition == null) {
            throw new IllegalArgumentException("Feature definition not found");
        }

        FeatureFlag feature = FeatureFlag.builder()
                .tenant(tenant)
                .featureCode(definition.code)
                .featureName(definition.name)
                .category(definition.category)
                .isBeta(definition.isBeta)
                .isEnabled(shouldEnableByDefault(definition, plan))
                .requiredPlan(definition.minimumPlans.isEmpty() ? null :
                        definition.minimumPlans.iterator().next().name())
                .build();

        // Set usage limits based on plan
        setUsageLimits(feature, plan);

        return feature;
    }

    private boolean shouldEnableByDefault(FeatureDefinition definition, SubscriptionPlan plan) {
        // Enable basic features by default
        if (definition.code.startsWith("BASIC_")) {
            return true;
        }

        // Enable if plan includes the feature
        return plan.hasFeature(definition.code);
    }

    private void setUsageLimits(FeatureFlag feature, SubscriptionPlan plan) {
        // Set usage limits based on feature and plan
        switch (feature.getFeatureCode()) {
            case "API_ACCESS" -> {
                feature.setUsageLimit(plan.getApiCallsPerDay());
                feature.setResetFrequency("DAILY");
            }
            case "DATA_EXPORT" -> {
                feature.setUsageLimit(getExportLimit(plan));
                feature.setResetFrequency("MONTHLY");
            }
            case "CUSTOM_DASHBOARDS" -> {
                feature.setUsageLimit(getDashboardLimit(plan));
            }
            default -> {
                // No limits
            }
        }
    }

    private boolean isEligibleForFeature(Tenant tenant, String featureCode) {
        FeatureDefinition definition = FEATURE_CATALOG.get(featureCode);
        if (definition == null) return false;

        SubscriptionPlan currentPlan = tenant.getSubscription().getPlan();

        return definition.minimumPlans.isEmpty() ||
                definition.minimumPlans.contains(currentPlan);
    }

    private boolean shouldEnableBasedOnRollout(Tenant tenant, int rolloutPercentage) {
        // Simple hash-based rollout
        int hash = tenant.getId().hashCode();
        return Math.abs(hash % 100) < rolloutPercentage;
    }

    private int getExportLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case FREEMIUM -> 5;
            case BASIC -> 20;
            case PROFESSIONAL -> 100;
            case ENTERPRISE, GOVERNMENT, ACADEMIC -> -1; // Unlimited
            default -> 10;
        };
    }

    private int getDashboardLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case FREEMIUM -> 1;
            case BASIC -> 5;
            case PROFESSIONAL -> 20;
            case ENTERPRISE, GOVERNMENT, ACADEMIC -> -1; // Unlimited
            default -> 3;
        };
    }

    // Inner classes

    private record FeatureDefinition(
            String code,
            String name,
            String category,
            Set<SubscriptionPlan> minimumPlans,
            Set<OrganizationType> suitableOrgTypes,
            boolean isBeta
    ) {}

    // Exceptions

    public static class FeatureNotFoundException extends RuntimeException {
        public FeatureNotFoundException(String message) {
            super(message);
        }
    }

    public static class FeatureNotAvailableException extends RuntimeException {
        public FeatureNotAvailableException(String message) {
            super(message);
        }
    }



}