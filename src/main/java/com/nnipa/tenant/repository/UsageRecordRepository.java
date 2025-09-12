package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID; /**
 * Repository for UsageRecord entity
 */
@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    List<UsageRecord> findBySubscriptionId(UUID subscriptionId);

    @Query("""
        SELECT u FROM UsageRecord u 
        WHERE u.subscription.id = :subscriptionId 
        AND u.usageDate BETWEEN :startDate AND :endDate
        ORDER BY u.usageDate DESC
        """)
    List<UsageRecord> findBySubscriptionAndDateRange(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT u.metricName, SUM(u.quantity) as total 
        FROM UsageRecord u 
        WHERE u.subscription.id = :subscriptionId 
        AND u.usageDate BETWEEN :startDate AND :endDate 
        GROUP BY u.metricName
        """)
    List<Object[]> aggregateUsageByMetric(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT SUM(u.amount) FROM UsageRecord u 
        WHERE u.subscription.id = :subscriptionId 
        AND u.isBillable = true 
        AND u.billedAt IS NULL
        """)
    Optional<Double> calculateUnbilledAmount(@Param("subscriptionId") UUID subscriptionId);

    @Modifying
    @Query("""
        UPDATE UsageRecord u 
        SET u.billedAt = :billedAt, u.invoiceId = :invoiceId 
        WHERE u.subscription.id = :subscriptionId 
        AND u.billedAt IS NULL
        """)
    int markAsBilled(@Param("subscriptionId") UUID subscriptionId,
                     @Param("billedAt") LocalDateTime billedAt,
                     @Param("invoiceId") String invoiceId);
}
