package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Subscription;
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
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("SELECT s FROM Subscription s WHERE s.tenant = :tenant AND s.subscriptionStatus = 'ACTIVE'")
    Optional<Subscription> findByTenantAndActiveStatus(@Param("tenant") Tenant tenant);

    @Query("SELECT s FROM Subscription s WHERE s.nextRenewalDate BETWEEN :start AND :end " +
            "AND s.subscriptionStatus = 'ACTIVE'")
    List<Subscription> findExpiringSubscriptions(@Param("start") Instant start,
                                                 @Param("end") Instant end);

    @Query("SELECT s FROM Subscription s WHERE s.trialEndDate BETWEEN :start AND :end " +
            "AND s.subscriptionStatus = 'ACTIVE'")
    List<Subscription> findEndingTrials(@Param("start") Instant start,
                                        @Param("end") Instant end);

    List<Subscription> findBySubscriptionStatus(String status);
}
