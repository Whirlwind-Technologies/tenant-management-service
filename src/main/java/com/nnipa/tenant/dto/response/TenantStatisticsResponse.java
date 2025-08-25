package com.nnipa.tenant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
    private List<Object[]> statisticsByType;

    @Schema(description = "Tenants by status")
    private Map<String, Long> tenantsByStatus;

    @Schema(description = "Tenants by plan")
    private Map<String, Long> tenantsByPlan;

    @Schema(description = "Average revenue per tenant")
    private Double averageRevenuePerTenant;

    @Schema(description = "Total monthly recurring revenue")
    private Double totalMRR;

    @Schema(description = "Growth rate percentage")
    private Double growthRate;
}