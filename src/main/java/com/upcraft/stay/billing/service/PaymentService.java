package com.upcraft.stay.billing.service;

import java.util.UUID;

import com.upcraft.stay.billing.dto.FinancialSummaryResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.dto.PaymentResponseDTO;
import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;
import com.upcraft.stay.billing.dto.ReversePaymentRequestDTO;
import com.upcraft.stay.billing.dto.TenantLedgerResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Service contract for payment recording, immutable reversals, receipts, and tenant financial ledgers.
 */
public interface PaymentService {

    PageResponseDTO<PaymentResponseDTO> listPayments(
            String principalEmail,
            UUID propertyId,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            UUID invoiceId,
            UUID tenantId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PaymentResponseDTO recordPayment(
            String principalEmail,
            UUID invoiceId,
            RecordPaymentRequestDTO recordPaymentRequestDTO
    );

    PaymentResponseDTO reversePayment(
            String principalEmail,
            UUID paymentId,
            ReversePaymentRequestDTO reversePaymentRequestDTO
    );

    PaymentReceiptResponseDTO getPaymentReceipt(String principalEmail, UUID paymentId);

    FinancialSummaryResponseDTO getFinancialSummary(
            String principalEmail,
            UUID propertyId,
            Integer billingYear,
            Integer billingMonth
    );

    TenantLedgerResponseDTO getTenantLedger(
            String principalEmail,
            UUID tenantId,
            int page,
            Integer size
    );
}

