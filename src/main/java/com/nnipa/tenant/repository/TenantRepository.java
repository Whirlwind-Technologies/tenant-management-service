package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    boolean existsByTenantCode(String tenantCode);

    boolean existsByOrganizationEmail(String email);

    List<Tenant> findByOrganizationType(OrganizationType type);

    List<Tenant> findByStatus(TenantStatus status);

    List<Tenant> findByStatusIn(List<TenantStatus> statuses);

    List<Tenant> findByParentTenant(Tenant parentTenant);

    Long countByOrganizationType(OrganizationType type);

    Long countByStatusIn(List<TenantStatus> statuses);

    @Query("SELECT t FROM Tenant t WHERE " +
            "LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.tenantCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.organizationEmail) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Tenant> searchTenants(@Param("search") String search, Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE t.status IN :statuses")
    Page<Tenant> findByStatusIn(@Param("statuses") List<TenantStatus> statuses, Pageable pageable);

    @Query("SELECT t FROM Tenant t LEFT JOIN t.subscription s " +
            "WHERE s.nextRenewalDate <= :expiryDate AND s.autoRenew = false")
    List<Tenant> findExpiringTenants(@Param("expiryDate") Instant expiryDate);

    @Query("SELECT t FROM Tenant t WHERE t.status = 'TRIAL' AND t.trialEndsAt <= :endDate")
    List<Tenant> findTrialsEndingSoon(@Param("endDate") Instant endDate);

    @Query("SELECT t FROM Tenant t WHERE t.organizationType = :type AND t.status = :status")
    List<Tenant> findByOrganizationTypeAndStatus(
            @Param("type") OrganizationType type,
            @Param("status") TenantStatus status
    );
}