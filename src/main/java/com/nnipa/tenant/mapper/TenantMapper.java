package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.*;
import com.nnipa.tenant.dto.response.*;
import com.nnipa.tenant.entity.*;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper for Tenant entity
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "settings", ignore = true)
    @Mapping(target = "featureFlags", ignore = true)
    Tenant toEntity(CreateTenantRequest request);

    @Mapping(target = "parentTenantId", source = "parentTenant.id")
    @Mapping(target = "parentTenantCode", source = "parentTenant.tenantCode")
    @Mapping(target = "subscription", ignore = true) // Will be mapped separately
    @Mapping(target = "enabledFeaturesCount", ignore = true) // Will be calculated
    @Mapping(target = "enabledFeatureCodes", ignore = true) // Will be fetched
    TenantResponse toResponse(Tenant tenant);

    TenantSummaryResponse toSummaryResponse(Tenant tenant);

    List<TenantSummaryResponse> toSummaryResponseList(List<Tenant> tenants);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Tenant tenant, UpdateTenantRequest request);
}

