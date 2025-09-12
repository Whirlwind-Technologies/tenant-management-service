package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.FeatureFlagRequest;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import org.mapstruct.*;

import java.util.List; /**
 * Mapper for FeatureFlag entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FeatureFlagMapper {

    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "isAvailable", ignore = true) // Will be calculated
    @Mapping(target = "config", source = "configJson")
    @Mapping(target = "metadata", source = "metadataJson")
    FeatureFlagResponse toResponse(FeatureFlag featureFlag);

    List<FeatureFlagResponse> toResponseList(List<FeatureFlag> featureFlags);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "configJson", source = "config")
    @Mapping(target = "metadataJson", source = "metadata")
    FeatureFlag toEntity(FeatureFlagRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "configJson", source = "config")
    @Mapping(target = "metadataJson", source = "metadata")
    void updateEntity(@MappingTarget FeatureFlag featureFlag, FeatureFlagRequest request);
}
