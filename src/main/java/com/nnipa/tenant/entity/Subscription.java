package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Subscription entity managing tenant billing and plan details
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_tenant", columnList = "tenant_id"),
        @Index(name = "idx_subscription_status", columnList = "subscription_status"),
        @Index(name = "idx_subscription_plan", columnList = "plan"),
        @Index(name = "idx_subscription_renewal", columnList = "next_renewal_date"),
        @Index(name = "idx_subscription_end", columnList = "end_date")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Where(clause = "is_deleted = false")
public class Subscription extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "plan", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;

    @Column(name = "subscription_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus subscriptionStatus;

    // Pricing Information
    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", precision = 10, scale = 2)
    private BigDecimal annualPrice;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "billing_cycle", length = 20)
    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;

    // Discount Information
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "discount_valid_until")
    private LocalDateTime discountValidUntil;

    // Subscription Lifecycle
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "trial_start_date")
    private LocalDateTime trialStartDate;

    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    // Renewal Information
    @Column(name = "next_renewal_date")
    private LocalDateTime nextRenewalDate;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    @Column(name = "renewal_reminder_sent")
    private Boolean renewalReminderSent = false;

    @Column(name = "last_renewed_at")
    private LocalDateTime lastRenewedAt;

    // Payment Information
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "last_payment_amount", precision = 10, scale = 2)
    private BigDecimal lastPaymentAmount;

    @Column(name = "last_payment_status", length = 30)
    private String lastPaymentStatus;

    @Column(name = "failed_payment_count")
    private Integer failedPaymentCount = 0;

    // External References
    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    // Related Entities
    @OneToOne(mappedBy = "subscription", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BillingDetails billingDetails;

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UsageRecord> usageRecords = new HashSet<>();

    @Override
    protected void onCreate() {
        super.onCreate();
        if (subscriptionStatus == null) {
            subscriptionStatus = SubscriptionStatus.PENDING;
        }
        if (startDate == null) {
            startDate = LocalDateTime.now();
        }
        if (autoRenew == null) {
            autoRenew = true;
        }
        if (failedPaymentCount == null) {
            failedPaymentCount = 0;
        }
        if (currency == null) {
            currency = "USD";
        }
    }

    // Business methods
    public void activate() {
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        if (this.startDate == null) {
            this.startDate = LocalDateTime.now();
        }
    }

    public void cancel(String reason) {
        this.subscriptionStatus = SubscriptionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
        this.autoRenew = false;
    }

    public void renew() {
        this.lastRenewedAt = LocalDateTime.now();
        this.nextRenewalDate = calculateNextRenewalDate();
        this.renewalReminderSent = false;
    }

    public boolean isTrialing() {
        return trialEndDate != null && trialEndDate.isAfter(LocalDateTime.now());
    }

    public boolean needsRenewal() {
        return nextRenewalDate != null &&
                nextRenewalDate.isBefore(LocalDateTime.now()) &&
                autoRenew;
    }

    private LocalDateTime calculateNextRenewalDate() {
        LocalDateTime baseDate = nextRenewalDate != null ? nextRenewalDate : LocalDateTime.now();
        return switch (billingCycle) {
            case MONTHLY -> baseDate.plusMonths(1);
            case QUARTERLY -> baseDate.plusMonths(3);
            case SEMI_ANNUAL -> baseDate.plusMonths(6);
            case ANNUAL -> baseDate.plusYears(1);
            default -> baseDate.plusMonths(1);
        };
    }
}