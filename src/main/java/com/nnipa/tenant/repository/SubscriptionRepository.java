package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.SubscriptionStatus;
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
import java.util.UUID; /**
 * Repository for Subscription entity
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    List<Subscription> findBySubscriptionStatus(SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.nextRenewalDate <= :date AND s.autoRenew = true")
    List<Subscription> findSubscriptionsToRenew(@Param("date") LocalDateTime date);

    @Query("SELECT s FROM Subscription s WHERE s.trialEndDate <= :date AND s.subscriptionStatus = 'TRIALING'")
    List<Subscription> findExpiringTrials(@Param("date") LocalDateTime date);

    @Query("SELECT s FROM Subscription s WHERE s.plan = :plan AND s.isDeleted = false")
    Page<Subscription> findByPlan(@Param("plan") SubscriptionPlan plan, Pageable pageable);

    @Query(value = """
        SELECT COUNT(*) FROM subscriptions
        WHERE subscription_status = :status
        AND is_deleted = false
        """, nativeQuery = true)
    long countByStatus(@Param("status") String status);

    @Modifying
    @Query("UPDATE Subscription s SET s.subscriptionStatus = :status WHERE s.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") SubscriptionStatus status);

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.failedPaymentCount >= :threshold 
        AND s.subscriptionStatus != 'CANCELLED'
        """)
    List<Subscription> findSubscriptionsWithFailedPayments(@Param("threshold") int threshold);
}
