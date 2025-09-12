package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.UpdateSubscriptionRequest;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.dto.response.SubscriptionSummaryResponse;
import com.nnipa.tenant.entity.Subscription;
import org.mapstruct.*; /**
 * Mapper for Subscription entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantCode", source = "tenant.tenantCode")
    @Mapping(target = "tenantName", source = "tenant.name")
    @Mapping(target = "billingDetails", ignore = true) // Will be mapped separately
    @Mapping(target = "currentMonthUsage", ignore = true) // Will be calculated
    @Mapping(target = "unbilledAmount", ignore = true) // Will be calculated
    SubscriptionResponse toResponse(Subscription subscription);

    SubscriptionSummaryResponse toSummaryResponse(Subscription subscription);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Subscription subscription, UpdateSubscriptionRequest request);
}
