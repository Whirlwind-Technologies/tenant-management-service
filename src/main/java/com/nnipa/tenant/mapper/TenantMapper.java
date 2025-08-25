package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "settings", ignore = true)
    @Mapping(target = "featureFlags", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Tenant toEntity(CreateTenantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantCode", ignore = true)
    @Mapping(target = "organizationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    Tenant toEntity(UpdateTenantRequest request);

    @Mapping(source = "subscription.plan", target = "subscriptionPlan")
    @Mapping(source = "subscription.subscriptionStatus", target = "subscriptionStatus")
    TenantResponse toResponse(Tenant tenant);

    void updateEntity(UpdateTenantRequest request, @MappingTarget Tenant tenant);
}