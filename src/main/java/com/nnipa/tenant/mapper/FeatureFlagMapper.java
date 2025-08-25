package com.nnipa.tenant.mapper;

import com.nnipa.tenant.dto.request.FeatureFlagRequest;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.temporal.ChronoUnit;

@Mapper(componentModel = "spring", imports = {ChronoUnit.class, java.time.Instant.class,})
public interface FeatureFlagMapper {

    @Mapping(expression = "java(feature.isAccessible())", target = "isAccessible")
    @Mapping(expression = "java(feature.getTrialEnabled() && feature.getEnabledUntil() != null ? (int)ChronoUnit.DAYS.between(Instant.now(), feature.getEnabledUntil()) : null)",
            target = "trialDaysRemaining")
    @Mapping(expression = "java(feature.getUsageLimit() != null && feature.getUsageLimit() > 0 ? (feature.getCurrentUsage() * 100 / feature.getUsageLimit()) : null)",
            target = "usagePercentage")
    FeatureFlagResponse toResponse(FeatureFlag feature);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "featureName", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FeatureFlag toEntity(FeatureFlagRequest request);
}