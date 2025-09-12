package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.BillingDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID; /**
 * Repository for BillingDetails entity
 */
@Repository
public interface BillingDetailsRepository extends JpaRepository<BillingDetails, UUID> {

    Optional<BillingDetails> findBySubscriptionId(UUID subscriptionId);

    @Query("""
        SELECT b FROM BillingDetails b 
        JOIN b.subscription s 
        WHERE s.tenant.id = :tenantId
        """)
    Optional<BillingDetails> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT b FROM BillingDetails b 
        WHERE b.taxExempt = true 
        AND b.isDeleted = false
        """)
    List<BillingDetails> findTaxExemptAccounts();
}
