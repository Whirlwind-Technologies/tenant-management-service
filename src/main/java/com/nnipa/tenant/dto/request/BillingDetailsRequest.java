package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * Request DTO for billing details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingDetailsRequest {

    @Email
    private String billingEmail;

    private String billingName;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    private String billingPhone;

    // Address
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;

    @Size(min = 2, max = 2)
    private String billingCountry;

    // Tax
    private String taxId;
    private String vatNumber;
    private Boolean taxExempt;
    private String taxExemptReason;

    // Payment Method
    private String paymentMethodType;

    // Purchase Order
    private String purchaseOrderNumber;
    private LocalDateTime purchaseOrderExpiry;

    // Preferences
    private String invoicePrefix;
    private String invoiceNotes;
    private Boolean sendInvoiceEmail;
}
