package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.enums.FeatureCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID; /**
 * Repository for FeatureFlag entity
 */
@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    List<FeatureFlag> findByTenantId(UUID tenantId);

    Optional<FeatureFlag> findByTenantIdAndFeatureCode(UUID tenantId, String featureCode);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenant.id = :tenantId AND f.isEnabled = true")
    List<FeatureFlag> findEnabledFeatures(@Param("tenantId") UUID tenantId);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenant.id = :tenantId AND f.category = :category")
    List<FeatureFlag> findByTenantAndCategory(@Param("tenantId") UUID tenantId,
                                              @Param("category") FeatureCategory category);

    @Modifying
    @Query("UPDATE FeatureFlag f SET f.isEnabled = :enabled WHERE f.id = :id")
    void updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);

    @Query("""
        SELECT f FROM FeatureFlag f 
        WHERE f.usageLimit IS NOT NULL 
        AND f.currentUsage >= f.usageLimit 
        AND f.isEnabled = true
        """)
    List<FeatureFlag> findFeaturesAtUsageLimit();

    @Query("""
        SELECT DISTINCT f.featureCode FROM FeatureFlag f 
        WHERE f.isDeleted = false
        ORDER BY f.featureCode
        """)
    List<String> findAllFeatureCodes();

    @Modifying
    @Query("""
        UPDATE FeatureFlag f 
        SET f.currentUsage = 0, f.lastResetAt = CURRENT_TIMESTAMP 
        WHERE f.resetFrequency = :frequency 
        AND f.lastResetAt <= :beforeDate
        """)
    int resetUsageCounters(@Param("frequency") String frequency,
                           @Param("beforeDate") LocalDateTime beforeDate);
}
