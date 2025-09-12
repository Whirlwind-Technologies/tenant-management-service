package com.nnipa.tenant.service;

import com.nnipa.tenant.config.TenantProperties;
import com.nnipa.tenant.dto.request.FeatureFlagRequest;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.FeatureCategory;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.ResourceNotFoundException;
import com.nnipa.tenant.mapper.FeatureFlagMapper;
import com.nnipa.tenant.repository.FeatureFlagRepository;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing feature flags
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final TenantRepository tenantRepository;
    private final FeatureFlagMapper featureFlagMapper;
    private final TenantEventPublisher eventPublisher;
    private final TenantProperties tenantProperties;

    /**
     * Initialize feature flags for a new tenant
     */
    @Transactional
    public void initializeFeatureFlags(Tenant tenant, Map<String, Boolean> initialFlags) {
        log.info("Initializing feature flags for tenant: {}", tenant.getTenantCode());

        // Get all available features
        List<String> allFeatures = getAllAvailableFeatures();

        for (String featureCode : allFeatures) {
            FeatureFlag flag = FeatureFlag.builder()
                    .tenant(tenant)
                    .featureCode(featureCode)
                    .featureName(formatFeatureName(featureCode))
                    .description(getFeatureDescription(featureCode))
                    .category(categorizeFeature(featureCode))
                    .isEnabled(shouldEnableByDefault(featureCode, tenant, initialFlags))
                    .isBeta(isBetaFeature(featureCode))
                    .isExperimental(isExperimentalFeature(featureCode))
                    .requiredPlan(getRequiredPlan(featureCode))
                    .rolloutPercentage(100)
                    .build();

            // Set usage limits for certain features
            setFeatureUsageLimits(flag, tenant);

            featureFlagRepository.save(flag);
        }

        log.info("Initialized {} feature flags for tenant: {}",
                allFeatures.size(), tenant.getTenantCode());
    }

    /**
     * Get all feature flags for a tenant
     */
    @Cacheable(value = "feature-flags", key = "#tenantId")
    public List<FeatureFlagResponse> getTenantFeatures(UUID tenantId) {
        log.debug("Fetching feature flags for tenant: {}", tenantId);

        List<FeatureFlag> flags = featureFlagRepository.findByTenantId(tenantId);

        return flags.stream()
                .map(flag -> {
                    FeatureFlagResponse response = featureFlagMapper.toResponse(flag);
                    response.setIsAvailable(flag.isAvailable());
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get enabled features for a tenant
     */
    public List<FeatureFlagResponse> getEnabledFeatures(UUID tenantId) {
        log.debug("Fetching enabled features for tenant: {}", tenantId);

        List<FeatureFlag> flags = featureFlagRepository.findEnabledFeatures(tenantId);

        return flags.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update feature flag
     */
    @Transactional
    @CacheEvict(value = "feature-flags", key = "#tenantId")
    public FeatureFlagResponse updateFeatureFlag(UUID tenantId, String featureCode,
                                                 boolean enabled, String updatedBy) {
        log.info("Updating feature {} for tenant: {}, enabled: {}",
                featureCode, tenantId, enabled);

        FeatureFlag flag = featureFlagRepository.findByTenantIdAndFeatureCode(tenantId, featureCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + featureCode));

        boolean wasEnabled = flag.getIsEnabled();

        if (enabled) {
            // Check if feature can be enabled
            if (!canEnableFeature(flag)) {
                throw new IllegalStateException("Feature cannot be enabled: " +
                        getEnableBlockReason(flag));
            }
            flag.enable();
        } else {
            flag.disable();
        }

        flag.setUpdatedBy(updatedBy);
        flag = featureFlagRepository.save(flag);

        // Publish event
        if (wasEnabled != enabled) {
            if (enabled) {
                eventPublisher.publishFeatureEnabledEvent(flag);
            } else {
                eventPublisher.publishFeatureDisabledEvent(flag);
            }
        }

        return featureFlagMapper.toResponse(flag);
    }

    /**
     * Create or update feature flag
     */
    @Transactional
    @CacheEvict(value = "feature-flags", key = "#tenantId")
    public FeatureFlagResponse createOrUpdateFeatureFlag(UUID tenantId,
                                                         FeatureFlagRequest request,
                                                         String updatedBy) {
        log.info("Creating/updating feature {} for tenant: {}",
                request.getFeatureCode(), tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        FeatureFlag flag = featureFlagRepository
                .findByTenantIdAndFeatureCode(tenantId, request.getFeatureCode())
                .orElse(FeatureFlag.builder()
                        .tenant(tenant)
                        .featureCode(request.getFeatureCode())
                        .build());

        // Update fields
        featureFlagMapper.updateEntity(flag, request);
        flag.setUpdatedBy(updatedBy);

        flag = featureFlagRepository.save(flag);

        return featureFlagMapper.toResponse(flag);
    }

    /**
     * Check if feature is enabled for tenant
     */
    public boolean isFeatureEnabled(UUID tenantId, String featureCode) {
        return featureFlagRepository.findByTenantIdAndFeatureCode(tenantId, featureCode)
                .map(FeatureFlag::isAvailable)
                .orElse(false);
    }

    /**
     * Increment feature usage
     */
    @Transactional
    public void incrementFeatureUsage(UUID tenantId, String featureCode) {
        featureFlagRepository.findByTenantIdAndFeatureCode(tenantId, featureCode)
                .ifPresent(flag -> {
                    flag.incrementUsage();
                    featureFlagRepository.save(flag);

                    // Check if limit reached
                    if (flag.getUsageLimit() != null &&
                            flag.getCurrentUsage() >= flag.getUsageLimit()) {
                        log.warn("Feature {} reached usage limit for tenant: {}",
                                featureCode, tenantId);
                        // Could trigger notification
                    }
                });
    }

    /**
     * Reset usage counters (scheduled job)
     */
    @Scheduled(cron = "0 0 0 * * ?") // Run at midnight every day
    @Transactional
    public void resetDailyUsageCounters() {
        log.info("Resetting daily usage counters");
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        int count = featureFlagRepository.resetUsageCounters("DAILY", yesterday);
        log.info("Reset {} daily usage counters", count);
    }

    @Scheduled(cron = "0 0 0 * * MON") // Run at midnight every Monday
    @Transactional
    public void resetWeeklyUsageCounters() {
        log.info("Resetting weekly usage counters");
        LocalDateTime lastWeek = LocalDateTime.now().minusWeeks(1);
        int count = featureFlagRepository.resetUsageCounters("WEEKLY", lastWeek);
        log.info("Reset {} weekly usage counters", count);
    }

    @Scheduled(cron = "0 0 0 1 * ?") // Run at midnight on the 1st of each month
    @Transactional
    public void resetMonthlyUsageCounters() {
        log.info("Resetting monthly usage counters");
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        int count = featureFlagRepository.resetUsageCounters("MONTHLY", lastMonth);
        log.info("Reset {} monthly usage counters", count);
    }

    /**
     * Check feature dependencies
     */
    private boolean checkDependencies(FeatureFlag flag) {
        if (flag.getDependsOn() == null || flag.getDependsOn().isEmpty()) {
            return true;
        }

        String[] dependencies = flag.getDependsOn().split(",");
        for (String dependency : dependencies) {
            if (!isFeatureEnabled(flag.getTenant().getId(), dependency.trim())) {
                log.debug("Dependency {} not enabled for feature {}",
                        dependency, flag.getFeatureCode());
                return false;
            }
        }

        return true;
    }

    /**
     * Check feature conflicts
     */
    private boolean checkConflicts(FeatureFlag flag) {
        if (flag.getConflictsWith() == null || flag.getConflictsWith().isEmpty()) {
            return true;
        }

        String[] conflicts = flag.getConflictsWith().split(",");
        for (String conflict : conflicts) {
            if (isFeatureEnabled(flag.getTenant().getId(), conflict.trim())) {
                log.debug("Conflict {} enabled, cannot enable feature {}",
                        conflict, flag.getFeatureCode());
                return false;
            }
        }

        return true;
    }

    /**
     * Check if feature can be enabled
     */
    private boolean canEnableFeature(FeatureFlag flag) {
        // Check dependencies
        if (!checkDependencies(flag)) {
            return false;
        }

        // Check conflicts
        if (!checkConflicts(flag)) {
            return false;
        }

        // Check plan requirements
        if (flag.getRequiredPlan() != null) {
            String tenantPlan = flag.getTenant().getSubscription().getPlan().name();
            if (!isPlanSufficient(tenantPlan, flag.getRequiredPlan())) {
                return false;
            }
        }

        // Check approval requirements
        if (flag.getRequiresApproval() && flag.getApprovedAt() == null) {
            return false;
        }

        return true;
    }

    /**
     * Get reason why feature cannot be enabled
     */
    private String getEnableBlockReason(FeatureFlag flag) {
        if (!checkDependencies(flag)) {
            return "Missing required dependencies: " + flag.getDependsOn();
        }

        if (!checkConflicts(flag)) {
            return "Conflicts with enabled features: " + flag.getConflictsWith();
        }

        if (flag.getRequiredPlan() != null) {
            String tenantPlan = flag.getTenant().getSubscription().getPlan().name();
            if (!isPlanSufficient(tenantPlan, flag.getRequiredPlan())) {
                return "Requires plan: " + flag.getRequiredPlan() + ", current: " + tenantPlan;
            }
        }

        if (flag.getRequiresApproval() && flag.getApprovedAt() == null) {
            return "Requires approval";
        }

        return "Unknown reason";
    }

    /**
     * Check if tenant plan is sufficient for feature
     */
    private boolean isPlanSufficient(String tenantPlan, String requiredPlan) {
        Map<String, Integer> planHierarchy = Map.of(
                "FREEMIUM", 0,
                "TRIAL", 0,
                "BASIC", 1,
                "PROFESSIONAL", 2,
                "ENTERPRISE", 3,
                "GOVERNMENT", 4,
                "ACADEMIC", 2,
                "CUSTOM", 5
        );

        return planHierarchy.getOrDefault(tenantPlan, 0) >=
                planHierarchy.getOrDefault(requiredPlan, 0);
    }

    /**
     * Get all available features
     */
    private List<String> getAllAvailableFeatures() {
        return Arrays.asList(
                "BASIC_ANALYTICS",
                "ADVANCED_ANALYTICS",
                "STANDARD_REPORTS",
                "CUSTOM_REPORTS",
                "EMAIL_NOTIFICATIONS",
                "SMS_NOTIFICATIONS",
                "WEBHOOK_INTEGRATION",
                "API_ACCESS",
                "DATA_EXPORT",
                "DATA_IMPORT",
                "CUSTOM_DASHBOARDS",
                "ML_MODELS",
                "PRIORITY_SUPPORT",
                "SSO_INTEGRATION",
                "AUDIT_LOGS",
                "COMPLIANCE_REPORTS",
                "MULTI_FACTOR_AUTH",
                "IP_WHITELISTING",
                "CUSTOM_BRANDING",
                "ADVANCED_SECURITY"
        );
    }

    /**
     * Format feature name from code
     */
    private String formatFeatureName(String featureCode) {
        String temp = featureCode.replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : temp.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Get feature description
     */
    private String getFeatureDescription(String featureCode) {
        Map<String, String> descriptions = Map.of(
                "BASIC_ANALYTICS", "Basic analytics and reporting capabilities",
                "ADVANCED_ANALYTICS", "Advanced analytics with ML insights",
                "API_ACCESS", "Full API access for integration",
                "CUSTOM_DASHBOARDS", "Create and customize dashboards",
                "ML_MODELS", "Access to machine learning models"
        );

        return descriptions.getOrDefault(featureCode,
                "Feature: " + formatFeatureName(featureCode));
    }

    /**
     * Categorize feature
     */
    private FeatureCategory categorizeFeature(String featureCode) {
        if (featureCode.contains("ANALYTICS") || featureCode.contains("REPORTS")) {
            return FeatureCategory.ANALYTICS;
        } else if (featureCode.contains("SECURITY") || featureCode.contains("AUTH")) {
            return FeatureCategory.SECURITY;
        } else if (featureCode.contains("INTEGRATION") || featureCode.contains("API")) {
            return FeatureCategory.INTEGRATION;
        } else if (featureCode.contains("DASHBOARD") || featureCode.contains("UI")) {
            return FeatureCategory.UI;
        } else if (featureCode.contains("DATA")) {
            return FeatureCategory.DATA;
        }

        return FeatureCategory.PREMIUM;
    }

    /**
     * Check if feature should be enabled by default
     */
    private boolean shouldEnableByDefault(String featureCode, Tenant tenant,
                                          Map<String, Boolean> initialFlags) {
        // Check initial flags first
        if (initialFlags != null && initialFlags.containsKey(featureCode)) {
            return initialFlags.get(featureCode);
        }

        // Check default enabled features from properties
        if (tenantProperties.getFeatureFlags().getDefaultEnabled().contains(featureCode)) {
            return true;
        }

        // Enable premium features for enterprise/government plans
        if (tenantProperties.getFeatureFlags().getPremiumFeatures().contains(featureCode)) {
            String plan = tenant.getSubscription().getPlan().name();
            return plan.equals("ENTERPRISE") || plan.equals("GOVERNMENT");
        }

        return false;
    }

    /**
     * Check if feature is beta
     */
    private boolean isBetaFeature(String featureCode) {
        return featureCode.contains("ML_") || featureCode.contains("ADVANCED_");
    }

    /**
     * Check if feature is experimental
     */
    private boolean isExperimentalFeature(String featureCode) {
        return featureCode.contains("EXPERIMENTAL_");
    }

    /**
     * Get required plan for feature
     */
    private String getRequiredPlan(String featureCode) {
        if (tenantProperties.getFeatureFlags().getPremiumFeatures().contains(featureCode)) {
            return "PROFESSIONAL";
        }

        if (featureCode.contains("ENTERPRISE_")) {
            return "ENTERPRISE";
        }

        return null;
    }

    /**
     * Set feature usage limits based on tenant plan
     */
    private void setFeatureUsageLimits(FeatureFlag flag, Tenant tenant) {
        String plan = tenant.getSubscription().getPlan().name();

        // Set API usage limits
        if (flag.getFeatureCode().equals("API_ACCESS")) {
            switch (plan) {
                case "BASIC" -> {
                    flag.setUsageLimit(1000);
                    flag.setResetFrequency("DAILY");
                }
                case "PROFESSIONAL" -> {
                    flag.setUsageLimit(10000);
                    flag.setResetFrequency("DAILY");
                }
                case "ENTERPRISE", "GOVERNMENT" -> {
                    flag.setUsageLimit(null); // Unlimited
                }
            }
        }

        // Set data export limits
        if (flag.getFeatureCode().equals("DATA_EXPORT")) {
            switch (plan) {
                case "BASIC" -> {
                    flag.setUsageLimit(10);
                    flag.setResetFrequency("MONTHLY");
                }
                case "PROFESSIONAL" -> {
                    flag.setUsageLimit(100);
                    flag.setResetFrequency("MONTHLY");
                }
                case "ENTERPRISE", "GOVERNMENT" -> {
                    flag.setUsageLimit(null); // Unlimited
                }
            }
        }
    }
}