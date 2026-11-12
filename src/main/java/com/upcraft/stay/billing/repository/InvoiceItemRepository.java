package com.upcraft.stay.billing.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.billing.entity.InvoiceItemEntity;

/**
 * Persistence access for itemized invoice lines.
 */
public interface InvoiceItemRepository extends JpaRepository<InvoiceItemEntity, UUID> {

    List<InvoiceItemEntity> findAllByInvoiceIdOrderBySortOrderAsc(UUID invoiceId);

    List<InvoiceItemEntity> findAllByInvoiceIdInOrderBySortOrderAsc(Collection<UUID> invoiceIds);
}

