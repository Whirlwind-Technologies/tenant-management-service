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

    List<FeatureFlag> findByTenant(Tenant tenant);

    Optional<FeatureFlag> findByTenantAndFeatureCode(Tenant tenant, String featureCode);

    List<FeatureFlag> findByTenantAndIsEnabled(Tenant tenant, Boolean isEnabled);

    List<FeatureFlag> findByResetFrequency(String resetFrequency);

    @Query("SELECT f FROM FeatureFlag f WHERE f.trialEnabled = true " +
            "AND f.enabledUntil < :now AND f.isEnabled = true")
    List<FeatureFlag> findExpiredTrials(@Param("now") Instant now);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenant = :tenant " +
            "AND f.category = :category AND f.isEnabled = true")
    List<FeatureFlag> findByTenantAndCategory(@Param("tenant") Tenant tenant,
                                              @Param("category") String category);
}