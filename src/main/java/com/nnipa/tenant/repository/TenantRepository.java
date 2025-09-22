package com.nnipa.tenant.repository;
import com.nnipa.tenant.entity.*;
import com.nnipa.tenant.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Tenant entity
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    Optional<Tenant> findByTenantCodeAndIsDeletedFalse(String tenantCode);

    boolean existsByTenantCode(String tenantCode);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    Page<Tenant> findByOrganizationType(OrganizationType type, Pageable pageable);

    /**
     * Find tenant by organization email
     */
    Optional<Tenant> findByOrganizationEmail(String organizationEmail);

    /**
     * Find tenant by organization email, excluding deleted tenants
     */
    Optional<Tenant> findByOrganizationEmailAndIsDeletedFalse(String organizationEmail);

    @Query("SELECT t FROM Tenant t WHERE t.parentTenant.id = :parentId AND t.isDeleted = false")
    List<Tenant> findChildTenants(@Param("parentId") UUID parentId);

    @Query("SELECT t FROM Tenant t WHERE t.trialEndsAt <= :date AND t.status = 'TRIAL'")
    List<Tenant> findExpiringTrials(@Param("date") LocalDateTime date);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status = :status AND t.isDeleted = false")
    long countByStatus(@Param("status") TenantStatus status);

    @Modifying
    @Query("UPDATE Tenant t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") TenantStatus status);

    @Query(value = """
        SELECT t.* FROM tenants t
        JOIN subscriptions s ON t.id = s.tenant_id
        WHERE s.next_renewal_date <= :date
        AND s.auto_renew = true
        AND t.is_deleted = false
        """, nativeQuery = true)
    List<Tenant> findTenantsNeedingRenewal(@Param("date") LocalDateTime date);

    /**
     * Check if a tenant exists by organization email
     */
    boolean existsByOrganizationEmailAndIsDeletedFalse(String organizationEmail);
}