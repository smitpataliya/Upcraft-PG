package com.upcraft.stay.billing.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.billing.entity.PaymentAdjustmentEntity;

/**
 * Persistence access for immutable payment reversal and adjustment logs.
 */
public interface PaymentAdjustmentRepository extends JpaRepository<PaymentAdjustmentEntity, UUID> {

    @EntityGraph(attributePaths = {"adjustedByUser", "payment"})
    List<PaymentAdjustmentEntity> findAllByInvoiceIdOrderByAdjustedAtDesc(UUID invoiceId);

    @EntityGraph(attributePaths = {"adjustedByUser"})
    List<PaymentAdjustmentEntity> findAllByPaymentIdOrderByAdjustedAtDesc(UUID paymentId);

    @EntityGraph(attributePaths = {"adjustedByUser", "payment"})
    List<PaymentAdjustmentEntity> findAllByPaymentTenantIdOrderByAdjustedAtDesc(UUID tenantId);
}

