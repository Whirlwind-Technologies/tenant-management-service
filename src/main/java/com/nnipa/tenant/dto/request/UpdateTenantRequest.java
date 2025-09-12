package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Request DTO for updating tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateTenantRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String displayName;

    @Email(message = "Invalid email format")
    private String organizationEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String organizationPhone;

    @Size(max = 500)
    private String organizationWebsite;

    // Address
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;

    @Size(min = 2, max = 2)
    private String country;

    // Limits
    @Min(1)
    private Integer maxUsers;

    @Min(1)
    private Integer maxProjects;

    @Min(1)
    private Integer storageQuotaGb;

    @Min(1)
    private Integer apiRateLimit;
}
