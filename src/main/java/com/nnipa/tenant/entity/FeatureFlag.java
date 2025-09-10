// FeatureFlag.java
package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "feature_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "source")
    private String source; // PLAN, OVERRIDE, CUSTOM

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}