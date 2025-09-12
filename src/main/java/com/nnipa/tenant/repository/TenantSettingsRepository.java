package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID; /**
 * Repository for TenantSettings entity
 */
@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {

    Optional<TenantSettings> findByTenantId(UUID tenantId);

    @Query("""
        SELECT s FROM TenantSettings s 
        WHERE s.enforceMfa = true 
        AND s.isDeleted = false
        """)
    List<TenantSettings> findTenantsWithMfaEnforced();

    @Query(value = """
        SELECT * FROM tenant_settings 
        WHERE custom_settings->>'dataClassification' = :classification
        AND is_deleted = false
        """, nativeQuery = true)
    List<TenantSettings> findByDataClassification(@Param("classification") String classification);
}
