package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Billing Details entity for managing payment information
 */
@Entity
@Table(name = "billing_details", indexes = {
        @Index(name = "idx_billing_subscription", columnList = "subscription_id")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Where(clause = "is_deleted = false")
public class BillingDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false, unique = true)
    private Subscription subscription;

    // Billing Contact
    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "billing_name", length = 255)
    private String billingName;

    @Column(name = "billing_phone", length = 50)
    private String billingPhone;

    // Billing Address
    @Column(name = "billing_address_line1", length = 255)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 255)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state", length = 100)
    private String billingState;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    @Column(name = "billing_country", length = 2)
    private String billingCountry;

    // Tax Information
    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "vat_number", length = 100)
    private String vatNumber;

    @Column(name = "tax_exempt", nullable = false)
    private Boolean taxExempt = false;

    @Column(name = "tax_exempt_reason", length = 255)
    private String taxExemptReason;

    // Payment Method (encrypted in production)
    @Column(name = "payment_method_type", length = 50)
    private String paymentMethodType;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_expiry_month")
    private Integer cardExpiryMonth;

    @Column(name = "card_expiry_year")
    private Integer cardExpiryYear;

    // Purchase Order
    @Column(name = "purchase_order_number", length = 100)
    private String purchaseOrderNumber;

    @Column(name = "purchase_order_expiry")
    private LocalDate purchaseOrderExpiry;

    // Invoice Preferences
    @Column(name = "invoice_prefix", length = 20)
    private String invoicePrefix;

    @Column(name = "invoice_notes", columnDefinition = "TEXT")
    private String invoiceNotes;

    @Column(name = "send_invoice_email", nullable = false)
    private Boolean sendInvoiceEmail = true;
}

