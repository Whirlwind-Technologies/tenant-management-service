package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Invoice> findByDueDateBeforeAndStatus(Instant date, String status);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}