package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.BillingDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingDetailsRepository extends JpaRepository<BillingDetails, UUID> {
    Optional<BillingDetails> findBySubscriptionId(UUID subscriptionId);
}
