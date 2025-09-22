package com.nnipa.tenant.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.nnipa.proto.tenant.*;
import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.exception.TenantNotFoundException;
import com.nnipa.tenant.repository.TenantRepository;
import com.nnipa.tenant.service.TenantService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * gRPC Service implementation for Tenant Management
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TenantGrpcService extends TenantServiceGrpc.TenantServiceImplBase {

    private final TenantService tenantService;
    private final TenantRepository tenantRepository;

    /**
     * Create a new tenant synchronously
     */
    @Override
    @Transactional
    public void createTenant(
            com.nnipa.proto.tenant.CreateTenantRequest request,
            StreamObserver<CreateTenantResponse> responseObserver) {

        log.info("gRPC: Creating tenant with name: {} and correlation ID: {}",
                request.getName(), request.getCorrelationId());

        try {
            // Map gRPC request to internal DTO
            CreateTenantRequest internalRequest = CreateTenantRequest.builder()
                    .tenantCode(request.getTenantCode().isEmpty() ?
                            generateTenantCode(request.getName()) : request.getTenantCode())
                    .name(request.getName())
                    .organizationEmail(request.getEmail())
                    .organizationType(mapOrganizationType(request.getOrganizationType()))
                    .subscriptionPlan(mapSubscriptionPlan(request.getSubscriptionPlan()))
                    .build();

            // Create tenant using existing service
            TenantResponse tenantResponse = tenantService.createTenant(
                    internalRequest,
                    request.getCreatedBy(),
                    request.getCorrelationId()
            );

            // FIXED: Build proper response with actual tenant data
            CreateTenantResponse response = CreateTenantResponse.newBuilder()
                    .setTenantId(tenantResponse.getId().toString())  // Use actual tenant ID
                    .setTenantCode(tenantResponse.getTenantCode())   // Use actual tenant code
                    .setName(tenantResponse.getName())
                    .setStatus(tenantResponse.getStatus().name())
                    .setCreatedAt(toTimestamp(tenantResponse.getCreatedAt()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Successfully created tenant with ID: {}", tenantResponse.getId());

        } catch (Exception e) {
            log.error("gRPC: Error creating tenant", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to create tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Get tenant by ID
     */
    @Override
    public void getTenant(
            GetTenantRequest request,
            StreamObserver<GetTenantResponse> responseObserver) {

        log.debug("gRPC: Getting tenant with ID: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

            GetTenantResponse response = buildGetTenantResponse(tenant);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found: {}", request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error getting tenant", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Get tenant by code
     */
    @Override
    public void getTenantByCode(
            GetTenantByCodeRequest request,
            StreamObserver<GetTenantResponse> responseObserver) {

        log.debug("gRPC: Getting tenant with code: {}", request.getTenantCode());

        try {
            Tenant tenant = tenantRepository.findByTenantCode(request.getTenantCode())
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found with code: " + request.getTenantCode()));

            GetTenantResponse response = buildGetTenantResponse(tenant);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found with code: {}", request.getTenantCode());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error getting tenant by code", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Check if tenant exists
     */
    @Override
    public void tenantExists(
            TenantExistsRequest request,
            StreamObserver<TenantExistsResponse> responseObserver) {

        log.debug("gRPC: Checking if tenant exists: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            boolean exists = tenantRepository.existsById(tenantId);

            TenantExistsResponse.Builder responseBuilder = TenantExistsResponse.newBuilder()
                    .setExists(exists);

            if (exists) {
                Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                if (tenant != null) {
                    responseBuilder.setStatus(tenant.getStatus().name());
                }
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.warn("gRPC: Invalid tenant ID format: {}", request.getTenantId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid tenant ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error checking tenant existence", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to check tenant existence: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Update tenant
     */
    @Override
    @Transactional
    public void updateTenant(
            UpdateTenantRequest request,
            StreamObserver<UpdateTenantResponse> responseObserver) {

        log.info("gRPC: Updating tenant: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

            // Update fields if provided
            if (request.hasName()) {
                tenant.setName(request.getName());
            }
            if (request.hasEmail()) {
                tenant.setOrganizationEmail(request.getEmail());
            }
            if (request.hasSubscriptionPlan()) {
                // You might want to handle subscription plan changes through subscription service
                log.info("Subscription plan change requested for tenant: {} to plan: {}",
                        tenantId, request.getSubscriptionPlan());
            }

            // Update metadata
            if (!request.getMetadataMap().isEmpty()) {
                tenant.getMetadata().putAll(request.getMetadataMap());
            }

            tenant.setUpdatedBy(request.getUpdatedBy());
            tenant = tenantRepository.save(tenant);

            UpdateTenantResponse response = UpdateTenantResponse.newBuilder()
                    .setTenantId(tenant.getId().toString())
                    .setStatus(tenant.getStatus().name())
                    .setUpdatedAt(toTimestamp(tenant.getUpdatedAt()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Successfully updated tenant: {}", tenantId);

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found for update: {}", request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error updating tenant", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to update tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Activate tenant
     */
    @Override
    @Transactional
    public void activateTenant(
            ActivateTenantRequest request,
            StreamObserver<Empty> responseObserver) {

        log.info("gRPC: Activating tenant: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            tenantService.activateTenant(tenantId, request.getActivatedBy());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

            log.info("gRPC: Successfully activated tenant: {}", tenantId);

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found for activation: {}", request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error activating tenant", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to activate tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Suspend tenant
     */
    @Override
    @Transactional
    public void suspendTenant(
            SuspendTenantRequest request,
            StreamObserver<Empty> responseObserver) {

        log.info("gRPC: Suspending tenant: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            tenantService.suspendTenant(tenantId, request.getReason(), request.getSuspendedBy());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

            log.info("gRPC: Successfully suspended tenant: {}", tenantId);

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found for suspension: {}", request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error suspending tenant", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to suspend tenant: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Get tenant status
     */
    @Override
    public void getTenantStatus(
            GetTenantStatusRequest request,
            StreamObserver<GetTenantStatusResponse> responseObserver) {

        log.debug("gRPC: Getting status for tenant: {}", request.getTenantId());

        try {
            UUID tenantId = UUID.fromString(request.getTenantId());
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

            GetTenantStatusResponse response = GetTenantStatusResponse.newBuilder()
                    .setTenantId(tenant.getId().toString())
                    .setStatus(tenant.getStatus().name())
                    .setIsActive(tenant.getStatus() == TenantStatus.ACTIVE)
                    .setUserCount(tenant.getUserCount() != null ? tenant.getUserCount() : 0)
                    .setSubscriptionStatus(tenant.getSubscription() != null ?
                            tenant.getSubscription().getSubscriptionStatus().name() : "NONE")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (TenantNotFoundException e) {
            log.warn("gRPC: Tenant not found for status check: {}", request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error getting tenant status", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get tenant status: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Helper methods

    private GetTenantResponse buildGetTenantResponse(Tenant tenant) {
        GetTenantResponse.Builder builder = GetTenantResponse.newBuilder()
                .setTenantId(tenant.getId().toString())
                .setTenantCode(tenant.getTenantCode())
                .setName(tenant.getName())
                .setStatus(tenant.getStatus().name())
                .setOrganizationType(tenant.getOrganizationType().name())
                .setCreatedAt(toTimestamp(tenant.getCreatedAt()));

        if (tenant.getOrganizationEmail() != null) {
            builder.setEmail(tenant.getOrganizationEmail());
        }
        if (tenant.getSubscription() != null) {
            builder.setSubscriptionPlan(tenant.getSubscription().getPlan().name());
        }
        if (tenant.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(tenant.getUpdatedAt()));
        }
        if (tenant.getMetadata() != null) {
            builder.putAllMetadata(tenant.getMetadata());
        }

        return builder.build();
    }

    private String generateTenantCode(String name) {
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .substring(0, Math.min(name.length(), 6)) +
                UUID.randomUUID().toString().substring(0, 4);
    }

    private OrganizationType mapOrganizationType(String type) {
        if (type == null || type.isEmpty()) {
            return OrganizationType.CORPORATION;
        }
        try {
            return OrganizationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OrganizationType.CORPORATION;
        }
    }

    private SubscriptionPlan mapSubscriptionPlan(String plan) {
        if (plan == null || plan.isEmpty()) {
            return SubscriptionPlan.FREEMIUM;
        }
        try {
            return SubscriptionPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SubscriptionPlan.FREEMIUM;
        }
    }

    private Timestamp toTimestamp(java.time.LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return Timestamp.getDefaultInstance();
        }
        Instant instant = localDateTime.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}