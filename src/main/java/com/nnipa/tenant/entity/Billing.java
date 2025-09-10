package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.BillingStatus;
import com.nnipa.tenant.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_billing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "subscription_plan", nullable = false)
    private String subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingStatus status;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "monthly_amount", precision = 10, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "auto_renew")
    private Boolean autoRenew;

    @Column(name = "user_limit")
    private Integer userLimit;

    @Column(name = "storage_quota_gb")
    private Integer storageQuotaGb;

    @Column(name = "last_payment_date")
    private Instant lastPaymentDate;

    @Column(name = "last_payment_amount", precision = 10, scale = 2)
    private BigDecimal lastPaymentAmount;

    @Column(name = "failed_payment_count")
    private Integer failedPaymentCount;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "subscription_ends_at")
    private Instant subscriptionEndsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}