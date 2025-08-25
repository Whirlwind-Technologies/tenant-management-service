package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Subscription entity managing tenant billing and plan details.
 * Supports flexible pricing models for different organization types.
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_tenant", columnList = "tenant_id"),
        @Index(name = "idx_subscription_plan", columnList = "plan"),
        @Index(name = "idx_subscription_status", columnList = "subscription_status"),
        @Index(name = "idx_subscription_renewal", columnList = "next_renewal_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"tenant", "billingDetails", "usageRecords"})
@EqualsAndHashCode(callSuper = true, exclude = {"tenant", "billingDetails", "usageRecords"})
@Where(clause = "is_deleted = false")
public class Subscription extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    private SubscriptionPlan plan;

    @Column(name = "subscription_status", nullable = false, length = 30)
    private String subscriptionStatus; // ACTIVE, PAST_DUE, CANCELED, PAUSED

    // Pricing Details
    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", precision = 10, scale = 2)
    private BigDecimal annualPrice;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle; // MONTHLY, ANNUAL, CUSTOM

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    // Contract Details (for Enterprise/Government)
    @Column(name = "contract_id", length = 100)
    private String contractId;

    @Column(name = "contract_start_date")
    private Instant contractStartDate;

    @Column(name = "contract_end_date")
    private Instant contractEndDate;

    @Column(name = "contract_value", precision = 12, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "purchase_order_number", length = 100)
    private String purchaseOrderNumber;

    // Subscription Dates
    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "trial_start_date")
    private Instant trialStartDate;

    @Column(name = "trial_end_date")
    private Instant trialEndDate;

    @Column(name = "next_renewal_date")
    private Instant nextRenewalDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // Payment Information
    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // CREDIT_CARD, INVOICE, WIRE_TRANSFER, ACH

    @Column(name = "payment_terms", length = 50)
    private String paymentTerms; // NET_30, NET_60, IMMEDIATE

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    @Column(name = "last_payment_date")
    private Instant lastPaymentDate;

    @Column(name = "last_payment_amount", precision = 10, scale = 2)
    private BigDecimal lastPaymentAmount;

    @Column(name = "next_payment_date")
    private Instant nextPaymentDate;

    @Column(name = "next_payment_amount", precision = 10, scale = 2)
    private BigDecimal nextPaymentAmount;

    // Usage-based Billing
    @Column(name = "is_usage_based", nullable = false)
    private Boolean isUsageBased = false;

    @Column(name = "base_fee", precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "overage_rate", precision = 10, scale = 4)
    private BigDecimal overageRate;

    @Column(name = "usage_cap")
    private Integer usageCap;

    // Resource Limits (can override plan defaults)
    @Column(name = "custom_max_users")
    private Integer customMaxUsers;

    @Column(name = "custom_max_projects")
    private Integer customMaxProjects;

    @Column(name = "custom_storage_gb")
    private Integer customStorageGb;

    @Column(name = "custom_api_calls_per_day")
    private Integer customApiCallsPerDay;

    @Column(name = "custom_compute_units")
    private Integer customComputeUnits;

    // Billing Details
    @OneToOne(mappedBy = "subscription", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BillingDetails billingDetails;

    // Usage Records
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UsageRecord> usageRecords = new HashSet<>();

    // Add-ons and Features
    @ElementCollection
    @CollectionTable(
            name = "subscription_addons",
            joinColumns = @JoinColumn(name = "subscription_id")
    )
    @Column(name = "addon")
    private Set<String> addons = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "subscription_custom_features",
            joinColumns = @JoinColumn(name = "subscription_id")
    )
    @Column(name = "feature")
    private Set<String> customFeatures = new HashSet<>();

    // Metadata
    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId; // ID from payment processor

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Helper Methods

    /**
     * Checks if the subscription is currently active.
     */
    public boolean isActive() {
        return "ACTIVE".equals(subscriptionStatus) &&
                (endDate == null || endDate.isAfter(Instant.now()));
    }

    /**
     * Checks if the subscription is in trial period.
     */
    public boolean isInTrial() {
        return trialEndDate != null &&
                trialEndDate.isAfter(Instant.now()) &&
                (trialStartDate == null || trialStartDate.isBefore(Instant.now()));
    }

    /**
     * Gets the effective maximum users for this subscription.
     */
    public int getEffectiveMaxUsers() {
        return customMaxUsers != null ? customMaxUsers : plan.getMaxUsers();
    }

    /**
     * Gets the effective storage quota in GB.
     */
    public int getEffectiveStorageGb() {
        return customStorageGb != null ? customStorageGb : plan.getStorageGb();
    }

    /**
     * Calculates the current monthly cost.
     */
    public BigDecimal calculateMonthlyCost() {
        BigDecimal cost = monthlyPrice != null ? monthlyPrice : plan.getBaseMonthlyPrice();
        if (cost != null && discountPercentage != null) {
            BigDecimal discount = cost.multiply(discountPercentage).divide(BigDecimal.valueOf(100));
            cost = cost.subtract(discount);
        }
        return cost;
    }
}