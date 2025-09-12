package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map; /**
 * Usage Record entity for tracking metered billing
 */
@Entity
@Table(name = "usage_records", indexes = {
        @Index(name = "idx_usage_subscription", columnList = "subscription_id"),
        @Index(name = "idx_usage_date", columnList = "usage_date"),
        @Index(name = "idx_usage_metric", columnList = "metric_name"),
        @Index(name = "idx_usage_subscription_date", columnList = "subscription_id,usage_date")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Where(clause = "is_deleted = false")
public class UsageRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "metric_category", length = 50)
    private String metricCategory;

    // Quantities and billing
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "rate", precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    // Billing flags
    @Column(name = "is_billable", nullable = false)
    private Boolean isBillable = true;

    @Column(name = "is_overage", nullable = false)
    private Boolean isOverage = false;

    @Column(name = "included_quantity", precision = 15, scale = 4)
    private BigDecimal includedQuantity;

    @Column(name = "overage_quantity", precision = 15, scale = 4)
    private BigDecimal overageQuantity;

    // Metadata
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new HashMap<>();

    // Tracking
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "billed_at")
    private LocalDateTime billedAt;

    @Column(name = "invoice_id", length = 100)
    private String invoiceId;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (isBillable == null) {
            isBillable = true;
        }
        if (isOverage == null) {
            isOverage = false;
        }
    }
}
