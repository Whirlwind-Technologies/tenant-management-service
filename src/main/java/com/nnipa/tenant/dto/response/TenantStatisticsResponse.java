package com.nnipa.tenant.dto.response;

import com.nnipa.tenant.enums.OrganizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant statistics response")
public class TenantStatisticsResponse {

    @Schema(description = "Total number of tenants")
    private long totalTenants;

    @Schema(description = "Number of active tenants")
    private long activeTenants;

    @Schema(description = "Statistics by organization type")
    private Map<OrganizationType, Long> statisticsByType;

    @Schema(description = "Number of trial tenants")
    private long trialTenants;

    @Schema(description = "Number of suspended tenants")
    private long suspendedTenants;

    @Schema(description = "Number of expired tenants")
    private long expiredTenants;
}