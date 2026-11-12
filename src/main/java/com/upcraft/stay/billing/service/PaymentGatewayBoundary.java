package com.upcraft.stay.billing.service;

import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;

/**
 * Integration boundary decoupling manual payment recording from future payment gateways (e.g. Razorpay).
 */
public interface PaymentGatewayBoundary {

    /**
     * Validates payment method semantics and returns normalized provider confirmation state.
     *
     * @param paymentRequest payment payload
     * @return gateway recording result
     */
    GatewayRecordingResult verifyAndResolve(RecordPaymentRequestDTO paymentRequest);
}

