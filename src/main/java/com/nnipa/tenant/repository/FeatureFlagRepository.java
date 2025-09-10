package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    List<FeatureFlag> findByTenantId(UUID tenantId);

    Optional<FeatureFlag> findByTenantIdAndFeatureName(UUID tenantId, String featureName);

    List<FeatureFlag> findByFeatureName(String featureName);

    List<FeatureFlag> findBySource(String source);
}