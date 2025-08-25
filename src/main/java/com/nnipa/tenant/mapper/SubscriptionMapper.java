package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.BillingDetailsRequest;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.dto.response.UsageResponse;
import com.nnipa.tenant.entity.BillingDetails;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.UsageRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.temporal.ChronoUnit;

@Mapper(componentModel = "spring", imports = {ChronoUnit.class, java.time.Instant.class})
public interface SubscriptionMapper {

    @Mapping(source = "tenant.id", target = "tenantId")
    @Mapping(source = "tenant.name", target = "tenantName")
    @Mapping(expression = "java(subscription.isInTrial())", target = "isInTrial")
    @Mapping(expression = "java(subscription.getNextRenewalDate() != null ? (int)ChronoUnit.DAYS.between(Instant.now(), subscription.getNextRenewalDate()) : null)",
            target = "daysUntilRenewal")
    SubscriptionResponse toResponse(Subscription subscription);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BillingDetails toBillingDetails(BillingDetailsRequest request);

    UsageResponse toUsageResponse(UsageRecord usageRecord);
}