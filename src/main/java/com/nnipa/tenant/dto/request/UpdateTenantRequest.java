package com.nnipa.tenant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for updating tenant information")
public class UpdateTenantRequest {

    @Size(min = 2, max = 255)
    @Schema(description = "Organization name")
    private String name;

    @Size(max = 255)
    @Schema(description = "Display name")
    private String displayName;

    @Size(max = 1000)
    @Schema(description = "Description")
    private String description;

    @Email
    @Schema(description = "Organization email")
    private String organizationEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    @Schema(description = "Organization phone")
    private String organizationPhone;

    @Pattern(regexp = "^https?://.*")
    @Schema(description = "Organization website")
    private String organizationWebsite;

    @Schema(description = "Address line 1")
    private String addressLine1;

    @Schema(description = "Address line 2")
    private String addressLine2;

    @Schema(description = "City")
    private String city;

    @Schema(description = "State or province")
    private String stateProvince;

    @Schema(description = "Postal code")
    private String postalCode;

    @Size(min = 2, max = 2)
    @Pattern(regexp = "^[A-Z]{2}$")
    @Schema(description = "Country code")
    private String country;

    @Schema(description = "Logo URL")
    private String logoUrl;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    @Schema(description = "Primary color")
    private String primaryColor;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    @Schema(description = "Secondary color")
    private String secondaryColor;

    @Schema(description = "Timezone")
    private String timezone;

    @Pattern(regexp = "^[a-z]{2}_[A-Z]{2}$")
    @Schema(description = "Locale")
    private String locale;

    @Schema(description = "Tags")
    private String tags;
}