package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    @Query("SELECT u FROM UsageRecord u WHERE u.subscription = :subscription " +
            "AND u.usageDate BETWEEN :startDate AND :endDate")
    List<UsageRecord> findBySubscriptionAndDateRange(@Param("subscription") Subscription subscription,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    @Query("SELECT u FROM UsageRecord u WHERE u.subscription = :subscription " +
            "AND u.metricName = :metricName AND u.usageDate = :date")
    List<UsageRecord> findBySubscriptionAndMetricAndDate(@Param("subscription") Subscription subscription,
                                                         @Param("metricName") String metricName,
                                                         @Param("date") LocalDate date);

    @Query("SELECT SUM(u.quantity) FROM UsageRecord u WHERE u.subscription = :subscription " +
            "AND u.metricCategory = :category AND u.usageDate BETWEEN :startDate AND :endDate")
    Double sumUsageByCategory(@Param("subscription") Subscription subscription,
                              @Param("category") String category,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);
}
