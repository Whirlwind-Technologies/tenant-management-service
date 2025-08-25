package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Usage record entity for tracking resource consumption per tenant.
 * Used for usage-based billing and monitoring.
 */
@Entity
@Table(name = "usage_records", indexes = {
        @Index(name = "idx_usage_subscription", columnList = "subscription_id"),
        @Index(name = "idx_usage_date", columnList = "usage_date"),
        @Index(name = "idx_usage_metric", columnList = "metric_name"),
        @Index(name = "idx_usage_subscription_date", columnList = "subscription_id, usage_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "subscription")
@EqualsAndHashCode(callSuper = true, exclude = "subscription")
public class UsageRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "metric_category", length = 50)
    private String metricCategory; // COMPUTE, STORAGE, API, DATA, USERS

    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", length = 50)
    private String unit; // GB, API_CALLS, COMPUTE_HOURS, USERS, PROJECTS

    @Column(name = "rate", precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_billable", nullable = false)
    private Boolean isBillable = true;

    @Column(name = "is_overage", nullable = false)
    private Boolean isOverage = false;

    @Column(name = "included_quantity", precision = 15, scale = 4)
    private BigDecimal includedQuantity;

    @Column(name = "overage_quantity", precision = 15, scale = 4)
    private BigDecimal overageQuantity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "billed_at")
    private Instant billedAt;

    @Column(name = "invoice_id", length = 100)
    private String invoiceId;

    // Helper Methods

    /**
     * Calculates the billable amount based on quantity and rate.
     */
    public BigDecimal calculateAmount() {
        if (!isBillable || rate == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal billableQuantity = isOverage && overageQuantity != null ?
                overageQuantity : quantity;

        amount = billableQuantity.multiply(rate);
        return amount;
    }

    /**
     * Marks the record as billed.
     */
    public void markAsBilled(String invoiceId) {
        this.billedAt = Instant.now();
        this.invoiceId = invoiceId;
    }
}