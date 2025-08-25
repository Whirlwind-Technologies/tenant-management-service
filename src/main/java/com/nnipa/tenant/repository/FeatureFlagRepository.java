package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByTenantAndFeatureCode(Tenant tenant, String featureCode);

    List<FeatureFlag> findByTenant(Tenant tenant);

    List<FeatureFlag> findByTenantAndIsEnabled(Tenant tenant, boolean isEnabled);

    @Query("SELECT f FROM FeatureFlag f WHERE f.resetFrequency = :frequency")
    List<FeatureFlag> findByResetFrequency(@Param("frequency") String frequency);

    @Query("SELECT f FROM FeatureFlag f WHERE f.trialEnabled = true " +
            "AND f.enabledUntil < :now AND f.isEnabled = true")
    List<FeatureFlag> findExpiredTrials(@Param("now") Instant now);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenant.id = :tenantId " +
            "AND f.category = :category AND f.isEnabled = true")
    List<FeatureFlag> findEnabledByTenantAndCategory(@Param("tenantId") UUID tenantId,
                                                     @Param("category") String category);
}