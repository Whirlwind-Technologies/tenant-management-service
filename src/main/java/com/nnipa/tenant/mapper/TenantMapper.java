package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        builder = @Builder(disableBuilder = true))
public interface TenantMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "settings", ignore = true)
    @Mapping(target = "featureFlags", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    // Explicitly set inherited BaseEntity fields with default values
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "version", expression = "java(0L)")
    Tenant toEntity(CreateTenantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantCode", ignore = true)
    @Mapping(target = "organizationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Tenant toEntity(UpdateTenantRequest request);

    @Mapping(source = "subscription.plan", target = "subscriptionPlan")
    @Mapping(source = "subscription.subscriptionStatus", target = "subscriptionStatus")
    TenantResponse toResponse(Tenant tenant);

    void updateEntity(UpdateTenantRequest request, @MappingTarget Tenant tenant);

    /**
     * After mapping method to ensure BaseEntity fields are properly initialized.
     * This is called automatically by MapStruct after the mapping.
     */
    @AfterMapping
    default void setBaseEntityDefaults(@MappingTarget Tenant tenant) {
        // Ensure critical fields are never null
        if (tenant.getIsDeleted() == null) {
            tenant.setIsDeleted(false);
        }
        if (tenant.getVersion() == null) {
            tenant.setVersion(0L);
        }
    }
}