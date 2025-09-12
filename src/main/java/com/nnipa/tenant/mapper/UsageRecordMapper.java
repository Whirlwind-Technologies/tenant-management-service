package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.UsageRecordRequest;
import com.nnipa.tenant.dto.response.UsageRecordResponse;
import com.nnipa.tenant.entity.UsageRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List; /**
 * Mapper for UsageRecord entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UsageRecordMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "metadata", source = "metadataJson")
    UsageRecordResponse toResponse(UsageRecord usageRecord);

    List<UsageRecordResponse> toResponseList(List<UsageRecord> usageRecords);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "metadataJson", source = "metadata")
    UsageRecord toEntity(UsageRecordRequest request);
}
