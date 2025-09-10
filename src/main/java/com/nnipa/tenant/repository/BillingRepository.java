package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Billing;
import com.nnipa.tenant.enums.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRepository extends JpaRepository<Billing, UUID> {

    Optional<Billing> findByTenantId(UUID tenantId);

    List<Billing> findByStatus(BillingStatus status);

    List<Billing> findByCurrentPeriodEndBeforeAndAutoRenewTrueAndStatusIn(
            Instant date, List<BillingStatus> statuses);

    List<Billing> findBySubscriptionPlan(String plan);
}