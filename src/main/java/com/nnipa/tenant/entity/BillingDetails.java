package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

/**
 * Billing details entity storing payment and invoice information.
 */
@Entity
@Table(name = "billing_details", indexes = {
        @Index(name = "idx_billing_subscription", columnList = "subscription_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "subscription")
@EqualsAndHashCode(callSuper = true, exclude = "subscription")
@Where(clause = "is_deleted = false")
public class BillingDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    // Billing Contact
    @Column(name = "billing_contact_name", length = 255)
    private String billingContactName;

    @Column(name = "billing_contact_email", nullable = false, length = 255)
    private String billingContactEmail;

    @Column(name = "billing_contact_phone", length = 50)
    private String billingContactPhone;

    // Billing Address
    @Column(name = "billing_address_line1", length = 255)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 255)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state_province", length = 100)
    private String billingStateProvince;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    @Column(name = "billing_country", length = 2)
    private String billingCountry; // ISO 3166-1 alpha-2

    // Tax Information
    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    @Column(name = "tax_exempt", nullable = false)
    private Boolean taxExempt = false;

    @Column(name = "tax_exempt_certificate", length = 255)
    private String taxExemptCertificate;

    // Payment Method Details
    @Column(name = "payment_method_type", length = 50)
    private String paymentMethodType; // CREDIT_CARD, BANK_ACCOUNT, INVOICE

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_exp_month")
    private Integer cardExpMonth;

    @Column(name = "card_exp_year")
    private Integer cardExpYear;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "bank_account_last_four", length = 4)
    private String bankAccountLastFour;

    @Column(name = "bank_routing_number", length = 20)
    private String bankRoutingNumber;

    // Invoice Settings
    @Column(name = "invoice_prefix", length = 20)
    private String invoicePrefix;

    @Column(name = "invoice_notes", columnDefinition = "TEXT")
    private String invoiceNotes;

    @Column(name = "invoice_footer", columnDefinition = "TEXT")
    private String invoiceFooter;

    @Column(name = "send_invoice_automatically", nullable = false)
    private Boolean sendInvoiceAutomatically = true;

    @Column(name = "invoice_delivery_email", length = 255)
    private String invoiceDeliveryEmail;

    // Additional Information
    @Column(name = "purchase_order_required", nullable = false)
    private Boolean purchaseOrderRequired = false;

    @Column(name = "payment_processor_customer_id", length = 255)
    private String paymentProcessorCustomerId;

    @Column(name = "payment_processor_subscription_id", length = 255)
    private String paymentProcessorSubscriptionId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}