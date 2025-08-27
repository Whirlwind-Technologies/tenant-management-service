package com.nnipa.tenant.dto.request;

import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for creating a new tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for creating a new tenant")
public class CreateTenantRequest {

    @NotBlank(message = "Tenant code is required")
    @Pattern(regexp = "^[A-Z0-9-]{3,50}$", message = "Tenant code must be 3-50 characters, uppercase letters, numbers, and hyphens only")
    @Schema(description = "Unique tenant code", example = "CORP-TECH-001")
    private String tenantCode;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    @Schema(description = "Organization name", example = "TechCorp Analytics")
    private String name;

    @Size(max = 255)
    @Schema(description = "Display name for UI", example = "TechCorp")
    private String displayName;

    @Size(max = 1000)
    @Schema(description = "Organization description")
    private String description;

    @Schema(description = "Organization type - if not provided, will be auto-detected")
    private OrganizationType organizationType;

    @NotBlank(message = "Organization email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Primary organization email", example = "admin@techcorp.com")
    private String organizationEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "Organization phone number", example = "+14155550100")
    private String organizationPhone;

    @Pattern(regexp = "^https?://.*", message = "Website must start with http:// or https://")
    @Schema(description = "Organization website", example = "https://techcorp.com")
    private String organizationWebsite;

    @Schema(description = "Tax identification number")
    private String taxId;

    @Schema(description = "Business license number")
    private String businessLicense;

    // Address
    @Schema(description = "Address line 1")
    private String addressLine1;

    @Schema(description = "Address line 2")
    private String addressLine2;

    @Schema(description = "City")
    private String city;

    @Schema(description = "State or province")
    private String stateProvince;

    @Schema(description = "Postal/ZIP code")
    private String postalCode;

    @Size(min = 2, max = 2)
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be 2-letter ISO code")
    @Schema(description = "Country code (ISO 3166-1 alpha-2)", example = "US")
    private String country;

    // Compliance
    @Schema(description = "Required compliance frameworks")
    private Set<String> complianceFrameworks;

    @Schema(description = "Data residency region", example = "US-EAST")
    private String dataResidencyRegion;

    // Resource limits
    @Min(1)
    @Max(100000)
    @Schema(description = "Maximum number of users")
    private Integer maxUsers;

    @Min(1)
    @Max(10000)
    @Schema(description = "Maximum number of projects")
    private Integer maxProjects;

    @Min(1)
    @Max(1000000)
    @Schema(description = "Storage quota in GB")
    private Integer storageQuotaGb;

    // Subscription plan
    @Schema(description = "Initial subscription plan", example = "PROFESSIONAL")
    private String subscriptionPlan;

    @Schema(description = "Start with trial period", defaultValue = "false")
    private Boolean startTrial;

    // Branding
    @Schema(description = "Organization logo URL")
    private String logoUrl;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be hex format (#RRGGBB)")
    @Schema(description = "Primary brand color", example = "#0066CC")
    private String primaryColor;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be hex format (#RRGGBB)")
    @Schema(description = "Secondary brand color", example = "#FF6600")
    private String secondaryColor;

    @Schema(description = "Timezone", example = "America/New_York")
    private String timezone;

    @Pattern(regexp = "^[a-z]{2}_[A-Z]{2}$", message = "Locale must be in format xx_XX")
    @Schema(description = "Locale", example = "en_US")
    private String locale;

    @Schema(description = "Comma-separated tags for categorization")
    private String tags;

    // Auto-provisioning
    @Schema(description = "Auto-activate tenant after creation", defaultValue = "false")
    private Boolean autoActivate;

    @Schema(description = "Send welcome email", defaultValue = "true")
    private Boolean sendWelcomeEmail;

    @Schema(description = "Security level (LOW, MEDIUM, HIGH, CRITICAL)")
    private String securityLevel;

    @Schema(description = "Isolation strategy override")
    private TenantIsolationStrategy isolationStrategy;

    @Schema(description = "Additional metadata as key-value pairs")
    private Map<String, String> metadata;

    @Schema(description = "Parent tenant ID for hierarchical organizations")
    private UUID parentTenantId;

    @Schema(description = "Skip automatic compliance assignment", defaultValue = "false")
    private Boolean skipComplianceAutoAssignment;
}