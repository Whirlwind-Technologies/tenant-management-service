package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.TenantSettingsRequest;
import com.nnipa.tenant.dto.response.TenantSettingsResponse;
import com.nnipa.tenant.entity.TenantSettings;
import org.mapstruct.*; /**
 * Mapper for TenantSettings entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantSettingsMapper {

    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "webhookUrls", source = "webhookUrls")
    @Mapping(target = "customSettings", source = "customSettings")
    TenantSettingsResponse toResponse(TenantSettings settings);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    TenantSettings toEntity(TenantSettingsRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget TenantSettings settings, TenantSettingsRequest request);
}
