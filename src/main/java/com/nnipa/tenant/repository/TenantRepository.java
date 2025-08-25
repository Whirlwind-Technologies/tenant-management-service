package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Tenant entity operations.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID>,
        JpaSpecificationExecutor<Tenant> {

    /**
     * Find tenant by tenant code.
     */
    Optional<Tenant> findByTenantCode(String tenantCode);

    /**
     * Find tenant by ID or tenant code.
     */
    @Query("SELECT t FROM Tenant t WHERE t.id = :id OR t.tenantCode = :code")
    Optional<Tenant> findByIdOrTenantCode(@Param("id") String id, @Param("code") String code);

    /**
     * Find all tenants by organization type.
     */
    List<Tenant> findByOrganizationType(OrganizationType organizationType);

    /**
     * Find all tenants by status.
     */
    List<Tenant> findByStatus(TenantStatus status);

    /**
     * Find all tenants by isolation strategy.
     */
    List<Tenant> findByIsolationStrategy(TenantIsolationStrategy strategy);

    /**
     * Find active tenants (ACTIVE or TRIAL status).
     */
    @Query("SELECT t FROM Tenant t WHERE t.status IN ('ACTIVE', 'TRIAL')")
    List<Tenant> findActiveTenants();

    /**
     * Find tenants expiring soon.
     */
    @Query("SELECT t FROM Tenant t WHERE t.expiresAt BETWEEN :now AND :futureDate")
    List<Tenant> findExpiringTenants(@Param("now") Instant now,
                                     @Param("futureDate") Instant futureDate);

    /**
     * Find trial tenants ending soon.
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = 'TRIAL' " +
            "AND t.trialEndsAt BETWEEN :now AND :futureDate")
    List<Tenant> findTrialEndingSoon(@Param("now") Instant now,
                                     @Param("futureDate") Instant futureDate);

    /**
     * Find child tenants of a parent tenant.
     */
    List<Tenant> findByParentTenant(Tenant parentTenant);

    /**
     * Count tenants by organization type.
     */
    long countByOrganizationType(OrganizationType organizationType);

    /**
     * Count active tenants.
     */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status IN ('ACTIVE', 'TRIAL')")
    long countActiveTenants();

    /**
     * Find tenants by data residency region.
     */
    List<Tenant> findByDataResidencyRegion(String region);

    /**
     * Check if tenant code exists.
     */
    boolean existsByTenantCode(String tenantCode);

    /**
     * Find tenants requiring specific compliance framework.
     */
    @Query("SELECT DISTINCT t FROM Tenant t JOIN t.complianceFrameworks cf " +
            "WHERE cf = :framework")
    List<Tenant> findByComplianceFramework(@Param("framework") String framework);

    /**
     * Find tenants by schema name (for schema-per-tenant strategy).
     */
    Optional<Tenant> findBySchemaName(String schemaName);

    /**
     * Find tenants by database name (for database-per-tenant strategy).
     */
    Optional<Tenant> findByDatabaseName(String databaseName);

    /**
     * Get tenant statistics by organization type.
     */
    @Query("SELECT t.organizationType, COUNT(t), " +
            "SUM(CASE WHEN t.status = 'ACTIVE' THEN 1 ELSE 0 END) " +
            "FROM Tenant t GROUP BY t.organizationType")
    List<Object[]> getTenantStatisticsByOrgType();

    /**
     * Find tenants pending verification.
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = 'PENDING_VERIFICATION' " +
            "AND t.createdAt < :cutoffDate")
    List<Tenant> findPendingVerificationOlderThan(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find suspended tenants with reason.
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = 'SUSPENDED' " +
            "AND t.suspensionReason LIKE %:reason%")
    List<Tenant> findSuspendedByReason(@Param("reason") String reason);
}