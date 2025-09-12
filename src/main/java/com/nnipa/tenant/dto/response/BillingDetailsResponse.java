package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response DTO for billing details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingDetailsResponse {

    private UUID id;
    private UUID subscriptionId;

    // Contact
    private String billingEmail;
    private String billingName;
    private String billingPhone;

    // Address
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;

    // Tax
    private String taxId;
    private String vatNumber;
    private Boolean taxExempt;
    private String taxExemptReason;

    // Payment Method (masked)
    private String paymentMethodType;
    private String cardLastFour;
    private String cardBrand;
    private Integer cardExpiryMonth;
    private Integer cardExpiryYear;

    // Purchase Order
    private String purchaseOrderNumber;
    private LocalDateTime purchaseOrderExpiry;

    // Preferences
    private String invoicePrefix;
    private String invoiceNotes;
    private Boolean sendInvoiceEmail;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
