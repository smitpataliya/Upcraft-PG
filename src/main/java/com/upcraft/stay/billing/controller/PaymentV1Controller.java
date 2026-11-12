package com.upcraft.stay.billing.controller;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.dto.PaymentResponseDTO;
import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;
import com.upcraft.stay.billing.dto.ReversePaymentRequestDTO;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Versioned REST controller for partial/full payment collection, reversals, and payment receipts.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentV1Controller {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<PaymentResponseDTO>>> listPayments(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "paidAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<PaymentResponseDTO> payments = paymentService.listPayments(
                principal.getName(), propertyId, paymentStatus, paymentMethod,
                invoiceId, tenantId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Payments loaded", payments, traceId()));
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PaymentResponseDTO>> recordPayment(
            Principal principal,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody RecordPaymentRequestDTO recordPaymentRequestDTO
    ) {
        PaymentResponseDTO payment = paymentService.recordPayment(
                principal.getName(), invoiceId, recordPaymentRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Payment recorded successfully", payment, traceId()));
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PaymentResponseDTO>> reversePayment(
            Principal principal,
            @PathVariable UUID paymentId,
            @Valid @RequestBody ReversePaymentRequestDTO reversePaymentRequestDTO
    ) {
        PaymentResponseDTO reversed = paymentService.reversePayment(
                principal.getName(), paymentId, reversePaymentRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Payment reversed with audit log", reversed, traceId()));
    }

    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<PaymentReceiptResponseDTO>> getPaymentReceipt(
            Principal principal,
            @PathVariable UUID paymentId
    ) {
        PaymentReceiptResponseDTO receipt = paymentService.getPaymentReceipt(principal.getName(), paymentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Payment receipt loaded", receipt, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

