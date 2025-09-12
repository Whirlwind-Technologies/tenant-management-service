package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.BillingDetailsRequest;
import com.nnipa.tenant.dto.response.BillingDetailsResponse;
import com.nnipa.tenant.dto.response.BillingDetailsSummaryResponse;
import com.nnipa.tenant.entity.BillingDetails;
import org.mapstruct.*; /**
 * Mapper for BillingDetails entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BillingDetailsMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    BillingDetailsResponse toResponse(BillingDetails billingDetails);

    BillingDetailsSummaryResponse toSummaryResponse(BillingDetails billingDetails);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    BillingDetails toEntity(BillingDetailsRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget BillingDetails billingDetails, BillingDetailsRequest request);
}
