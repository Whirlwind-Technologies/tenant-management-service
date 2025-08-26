package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.UsageRecord;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.repository.UsageRecordRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing usage quotas and resource consumption tracking.
 * Monitors usage against limits and triggers alerts when thresholds are reached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsageQuotaService {

    private final UsageRecordRepository usageRecordRepository;

    /**
     * Gets current usage statistics for a tenant.
     */
    @Cacheable(value = "usage-stats", key = "#tenant.id")
    public UsageStatistics getCurrentUsage(Tenant tenant) {
        log.debug("Calculating current usage for tenant: {}", tenant.getName());

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now();

        List<UsageRecord> records = usageRecordRepository.findBySubscriptionAndDateRange(
                tenant.getSubscription(), startDate, endDate
        );

        return calculateUsageStatistics(tenant, records);
    }

    /**
     * Checks if a tenant can use more of a specific resource.
     */
    public QuotaCheckResult checkQuota(Tenant tenant, String resourceType, BigDecimal requestedAmount) {
        log.debug("Checking quota for tenant {} - Resource: {}, Amount: {}",
                tenant.getName(), resourceType, requestedAmount);

        UsageStatistics current = getCurrentUsage(tenant);
        QuotaLimits limits = getQuotaLimits(tenant);

        BigDecimal currentUsage = getCurrentResourceUsage(current, resourceType);
        BigDecimal limit = getResourceLimit(limits, resourceType);

        if (limit.compareTo(BigDecimal.valueOf(-1)) == 0) {
            // Unlimited
            return QuotaCheckResult.allowed();
        }

        BigDecimal projectedUsage = currentUsage.add(requestedAmount);
        boolean allowed = projectedUsage.compareTo(limit) <= 0;

        if (!allowed) {
            log.warn("Quota exceeded for tenant {} - Resource: {}, Current: {}, Limit: {}, Requested: {}",
                    tenant.getName(), resourceType, currentUsage, limit, requestedAmount);
        }

        return QuotaCheckResult.builder()
                .allowed(allowed)
                .currentUsage(currentUsage)
                .limit(limit)
                .requestedAmount(requestedAmount)
                .remainingQuota(limit.subtract(currentUsage).max(BigDecimal.ZERO))
                .usagePercentage(calculatePercentage(currentUsage, limit))
                .build();
    }

    /**
     * Records resource consumption.
     */
    @Transactional
    public UsageRecord recordUsage(Tenant tenant, String resourceType, BigDecimal amount,
                                   String unit, Map<String, Object> metadata) {
        log.info("Recording usage for tenant {} - {}: {} {}",
                tenant.getName(), resourceType, amount, unit);

        // Check quota before recording
        QuotaCheckResult quotaCheck = checkQuota(tenant, resourceType, amount);

        UsageRecord record = UsageRecord.builder()
                .subscription(tenant.getSubscription())
                .usageDate(LocalDate.now())
                .metricName(resourceType)
                .metricCategory(determineCategory(resourceType))
                .quantity(amount)
                .unit(unit)
                .isBillable(true)
                .isOverage(false)
                .recordedAt(java.time.Instant.now())
                .build();

        // Check if this is overage
        if (!quotaCheck.isAllowed()) {
            record.setIsOverage(true);
            record.setOverageQuantity(amount);

            // Calculate overage charges
            BigDecimal overageRate = getOverageRate(tenant, resourceType);
            record.setRate(overageRate);
            record.calculateAmount();

            // Notify about overage
//            notificationService.sendOverageAlert(tenant, resourceType, amount, record.getAmount());
        }

        record = usageRecordRepository.save(record);

        // Check usage thresholds
        checkUsageThresholds(tenant, quotaCheck);

        return record;
    }

    /**
     * Gets quota limits for a tenant.
     */
    public QuotaLimits getQuotaLimits(Tenant tenant) {
        Subscription subscription = tenant.getSubscription();
        SubscriptionPlan plan = subscription.getPlan();

        return QuotaLimits.builder()
                .users(getEffectiveLimit(subscription.getCustomMaxUsers(), plan.getMaxUsers()))
                .projects(getEffectiveLimit(subscription.getCustomMaxProjects(), plan.getMaxProjects()))
                .storageGb(getEffectiveLimit(subscription.getCustomStorageGb(), plan.getStorageGb()))
                .apiCallsPerDay(getEffectiveLimit(subscription.getCustomApiCallsPerDay(),
                        plan.getApiCallsPerDay()))
                .computeUnits(getEffectiveLimit(subscription.getCustomComputeUnits(),
                        plan.getComputeUnitsPerMonth()))
                .dataExportsPerMonth(getDataExportLimit(plan))
                .reportsPerMonth(getReportLimit(plan))
                .build();
    }

    /**
     * Gets historical usage data.
     */
    public List<UsageRecord> getHistoricalUsage(Tenant tenant, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching historical usage for tenant {} from {} to {}",
                tenant.getName(), startDate, endDate);

        return usageRecordRepository.findBySubscriptionAndDateRange(
                tenant.getSubscription(), startDate, endDate
        );
    }

    /**
     * Calculates projected usage for the current billing period.
     */
    public UsageProjection calculateProjection(Tenant tenant) {
        log.debug("Calculating usage projection for tenant: {}", tenant.getName());

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        long daysInMonth = ChronoUnit.DAYS.between(startOfMonth, endOfMonth) + 1;
        long daysElapsed = ChronoUnit.DAYS.between(startOfMonth, today) + 1;

        UsageStatistics current = getCurrentUsage(tenant);
        QuotaLimits limits = getQuotaLimits(tenant);

        Map<String, BigDecimal> projections = new HashMap<>();
        Map<String, Boolean> warnings = new HashMap<>();

        // Project each metric
        projectMetric("storage", current.getStorageGb(), limits.getStorageGb(),
                daysElapsed, daysInMonth, projections, warnings);
        projectMetric("apiCalls", current.getApiCalls(), limits.getApiCallsPerDay().multiply(BigDecimal.valueOf(daysInMonth)),
                daysElapsed, daysInMonth, projections, warnings);
        projectMetric("compute", current.getComputeUnits(), limits.getComputeUnits(),
                daysElapsed, daysInMonth, projections, warnings);

        return UsageProjection.builder()
                .currentUsage(current)
                .projectedUsage(projections)
                .warningThresholds(warnings)
                .projectionDate(endOfMonth)
                .build();
    }

    /**
     * Resets daily usage counters.
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    @Transactional
    public void resetDailyQuotas() {
        log.info("Resetting daily usage quotas");

        // Daily quotas are tracked by date in usage records
        // No reset needed as we query by date

        log.info("Daily quota reset completed");
    }

    /**
     * Checks monthly usage and sends reports.
     */
    @Scheduled(cron = "0 0 1 1 * *") // Monthly on the 1st at 1 AM
    @Transactional
    public void monthlyUsageReport() {
        log.info("Generating monthly usage reports");

        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        LocalDate startDate = lastMonth.withDayOfMonth(1);
        LocalDate endDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());

        List<Tenant> activeTenants = getActiveTenants();

        for (Tenant tenant : activeTenants) {
            try {
                List<UsageRecord> records = getHistoricalUsage(tenant, startDate, endDate);
                UsageStatistics stats = calculateUsageStatistics(tenant, records);

                // Send usage report
//                notificationService.sendMonthlyUsageReport(tenant, stats, startDate, endDate);

                // Check for consistent overage
                checkConsistentOverage(tenant, records);

            } catch (Exception e) {
                log.error("Error generating usage report for tenant: {}", tenant.getName(), e);
            }
        }

        log.info("Monthly usage reports completed");
    }

    // Helper methods

    private UsageStatistics calculateUsageStatistics(Tenant tenant, List<UsageRecord> records) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal storageGb = BigDecimal.ZERO;
        BigDecimal apiCalls = BigDecimal.ZERO;
        BigDecimal computeUnits = BigDecimal.ZERO;
        BigDecimal dataTransferGb = BigDecimal.ZERO;
        int activeUsers = tenant.getMaxUsers() != null ? tenant.getMaxUsers() : 0;
        int activeProjects = 0;

        for (UsageRecord record : records) {
            if (record.getAmount() != null) {
                totalCost = totalCost.add(record.getAmount());
            }

            switch (record.getMetricCategory()) {
                case "STORAGE" -> storageGb = storageGb.add(record.getQuantity());
                case "API" -> apiCalls = apiCalls.add(record.getQuantity());
                case "COMPUTE" -> computeUnits = computeUnits.add(record.getQuantity());
                case "DATA" -> dataTransferGb = dataTransferGb.add(record.getQuantity());
            }
        }

        return UsageStatistics.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .periodStart(records.isEmpty() ? LocalDate.now() : records.get(0).getUsageDate())
                .periodEnd(LocalDate.now())
                .totalCost(totalCost)
                .storageGb(storageGb)
                .apiCalls(apiCalls)
                .computeUnits(computeUnits)
                .dataTransferGb(dataTransferGb)
                .activeUsers(activeUsers)
                .activeProjects(activeProjects)
                .build();
    }

    private String determineCategory(String resourceType) {
        if (resourceType.toLowerCase().contains("storage")) return "STORAGE";
        if (resourceType.toLowerCase().contains("api")) return "API";
        if (resourceType.toLowerCase().contains("compute")) return "COMPUTE";
        if (resourceType.toLowerCase().contains("user")) return "USERS";
        return "DATA";
    }

    private BigDecimal getCurrentResourceUsage(UsageStatistics stats, String resourceType) {
        return switch (resourceType.toUpperCase()) {
            case "STORAGE" -> stats.getStorageGb();
            case "API_CALLS" -> stats.getApiCalls();
            case "COMPUTE" -> stats.getComputeUnits();
            case "DATA_TRANSFER" -> stats.getDataTransferGb();
            case "USERS" -> BigDecimal.valueOf(stats.getActiveUsers());
            case "PROJECTS" -> BigDecimal.valueOf(stats.getActiveProjects());
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal getResourceLimit(QuotaLimits limits, String resourceType) {
        return switch (resourceType.toUpperCase()) {
            case "STORAGE" -> limits.getStorageGb();
            case "API_CALLS" -> limits.getApiCallsPerDay();
            case "COMPUTE" -> limits.getComputeUnits();
            case "USERS" -> limits.getUsers();
            case "PROJECTS" -> limits.getProjects();
            default -> BigDecimal.valueOf(-1);
        };
    }

    private BigDecimal getEffectiveLimit(Integer custom, int planDefault) {
        if (custom != null && custom > 0) {
            return BigDecimal.valueOf(custom);
        }
        return BigDecimal.valueOf(planDefault);
    }

    private BigDecimal getDataExportLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case FREEMIUM -> BigDecimal.valueOf(5);
            case BASIC -> BigDecimal.valueOf(50);
            case PROFESSIONAL -> BigDecimal.valueOf(500);
            default -> BigDecimal.valueOf(-1); // Unlimited
        };
    }

    private BigDecimal getReportLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case FREEMIUM -> BigDecimal.valueOf(10);
            case BASIC -> BigDecimal.valueOf(100);
            case PROFESSIONAL -> BigDecimal.valueOf(1000);
            default -> BigDecimal.valueOf(-1); // Unlimited
        };
    }

    private BigDecimal getOverageRate(Tenant tenant, String resourceType) {
        SubscriptionPlan plan = tenant.getSubscription().getPlan();

        return switch (resourceType.toUpperCase()) {
            case "STORAGE" -> BigDecimal.valueOf(0.10); // $0.10 per GB
            case "API_CALLS" -> BigDecimal.valueOf(0.001); // $0.001 per call
            case "COMPUTE" -> BigDecimal.valueOf(0.50); // $0.50 per unit
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal calculatePercentage(BigDecimal used, BigDecimal limit) {
        if (limit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return used.multiply(BigDecimal.valueOf(100))
                .divide(limit, 2, RoundingMode.HALF_UP);
    }

    private void checkUsageThresholds(Tenant tenant, QuotaCheckResult quotaCheck) {
        BigDecimal percentage = quotaCheck.getUsagePercentage();

        if (percentage.compareTo(BigDecimal.valueOf(90)) >= 0) {
//            notificationService.sendUsageAlert(tenant, "90% quota reached", quotaCheck);
        } else if (percentage.compareTo(BigDecimal.valueOf(75)) >= 0) {
//            notificationService.sendUsageAlert(tenant, "75% quota reached", quotaCheck);
        } else if (percentage.compareTo(BigDecimal.valueOf(50)) >= 0) {
            // Log only, no notification
            log.info("Tenant {} has reached 50% of quota", tenant.getName());
        }
    }

    private void projectMetric(String metric, BigDecimal current, BigDecimal limit,
                               long daysElapsed, long daysInMonth,
                               Map<String, BigDecimal> projections,
                               Map<String, Boolean> warnings) {
        if (daysElapsed > 0) {
            BigDecimal dailyAverage = current.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
            BigDecimal projected = dailyAverage.multiply(BigDecimal.valueOf(daysInMonth));
            projections.put(metric, projected);

            if (limit.compareTo(BigDecimal.valueOf(-1)) != 0) {
                warnings.put(metric, projected.compareTo(limit) > 0);
            }
        }
    }

    private List<Tenant> getActiveTenants() {
        // This would be injected from TenantService
        return List.of();
    }

    private void checkConsistentOverage(Tenant tenant, List<UsageRecord> records) {
        long overageCount = records.stream()
                .filter(UsageRecord::getIsOverage)
                .count();

        if (overageCount > 10) {
//            notificationService.sendUpgradeRecommendation(tenant,
//                    "Consistent overage detected. Consider upgrading your plan.");
        }
    }

    // DTOs

    @Data
    @Builder
    public static class UsageStatistics {
        private UUID tenantId;
        private String tenantName;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal totalCost;
        private BigDecimal storageGb;
        private BigDecimal apiCalls;
        private BigDecimal computeUnits;
        private BigDecimal dataTransferGb;
        private int activeUsers;
        private int activeProjects;
    }

    @Data
    @Builder
    public static class QuotaLimits {
        private BigDecimal users;
        private BigDecimal projects;
        private BigDecimal storageGb;
        private BigDecimal apiCallsPerDay;
        private BigDecimal computeUnits;
        private BigDecimal dataExportsPerMonth;
        private BigDecimal reportsPerMonth;
    }

    @Data
    @Builder
    public static class QuotaCheckResult {
        private boolean allowed;
        private BigDecimal currentUsage;
        private BigDecimal limit;
        private BigDecimal requestedAmount;
        private BigDecimal remainingQuota;
        private BigDecimal usagePercentage;

        public static QuotaCheckResult allowed() {
            return QuotaCheckResult.builder()
                    .allowed(true)
                    .currentUsage(BigDecimal.ZERO)
                    .limit(BigDecimal.valueOf(-1))
                    .remainingQuota(BigDecimal.valueOf(-1))
                    .usagePercentage(BigDecimal.ZERO)
                    .build();
        }
    }

    @Data
    @Builder
    public static class UsageProjection {
        private UsageStatistics currentUsage;
        private Map<String, BigDecimal> projectedUsage;
        private Map<String, Boolean> warningThresholds;
        private LocalDate projectionDate;
    }
}