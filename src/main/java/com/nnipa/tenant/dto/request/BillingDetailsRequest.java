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
@Schema(description = "Billing details request")
public class BillingDetailsRequest {

    @NotBlank(message = "Billing contact name is required")
    @Schema(description = "Billing contact name")
    private String billingContactName;

    @NotBlank(message = "Billing email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Billing email")
    private String billingContactEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    @Schema(description = "Billing phone")
    private String billingContactPhone;

    @NotBlank(message = "Billing address is required")
    @Schema(description = "Billing address line 1")
    private String billingAddressLine1;

    @Schema(description = "Billing address line 2")
    private String billingAddressLine2;

    @NotBlank(message = "City is required")
    @Schema(description = "City")
    private String billingCity;

    @NotBlank(message = "State/Province is required")
    @Schema(description = "State or province")
    private String billingStateProvince;

    @NotBlank(message = "Postal code is required")
    @Schema(description = "Postal code")
    private String billingPostalCode;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2)
    @Pattern(regexp = "^[A-Z]{2}$")
    @Schema(description = "Country code")
    private String billingCountry;

    @Schema(description = "Tax ID")
    private String taxId;

    @Schema(description = "VAT number")
    private String vatNumber;

    @Schema(description = "Tax exempt", defaultValue = "false")
    private Boolean taxExempt;

    @Schema(description = "Payment method type", example = "CREDIT_CARD")
    private String paymentMethodType;

    @Pattern(regexp = "^\\d{4}$")
    @Schema(description = "Card last four digits")
    private String cardLastFour;

    @Schema(description = "Card brand", example = "VISA")
    private String cardBrand;

    @Min(1) @Max(12)
    @Schema(description = "Card expiry month")
    private Integer cardExpMonth;

    @Min(2024) @Max(2050)
    @Schema(description = "Card expiry year")
    private Integer cardExpYear;
}
