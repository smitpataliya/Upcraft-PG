package com.upcraft.stay.billing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Persistence access for rent and charge payment receipts.
 */
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "invoice", "invoice.room", "invoice.bed", "recordedByUser"})
    Optional<PaymentEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "invoice", "recordedByUser"})
    Optional<PaymentEntity> findByInvoiceIdAndIdempotencyKey(UUID invoiceId, String idempotencyKey);

    @EntityGraph(attributePaths = {"property", "tenant", "invoice", "recordedByUser"})
    List<PaymentEntity> findAllByInvoiceIdOrderByPaidAtDescCreatedAtDesc(UUID invoiceId);

    @EntityGraph(attributePaths = {"property", "tenant", "invoice", "recordedByUser"})
    List<PaymentEntity> findAllByTenantIdOrderByPaidAtDescCreatedAtDesc(UUID tenantId);

    @EntityGraph(attributePaths = {"property", "tenant", "invoice", "recordedByUser"})
    @Query("""
            SELECT pay FROM PaymentEntity pay
            JOIN pay.property p
            JOIN pay.tenant t
            JOIN pay.invoice inv
            WHERE p.id IN :propertyIds
              AND (:hasStatusFilter = false OR pay.paymentStatus = :paymentStatus)
              AND (:hasMethodFilter = false OR pay.paymentMethod = :paymentMethod)
              AND (:hasInvoiceFilter = false OR inv.id = :invoiceId)
              AND (:hasTenantFilter = false OR t.id = :tenantId)
            """)
    Page<PaymentEntity> findAccessiblePayments(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("hasMethodFilter") boolean hasMethodFilter,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("hasInvoiceFilter") boolean hasInvoiceFilter,
            @Param("invoiceId") UUID invoiceId,
            @Param("hasTenantFilter") boolean hasTenantFilter,
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    long countByOrganizationId(UUID organizationId);
}

