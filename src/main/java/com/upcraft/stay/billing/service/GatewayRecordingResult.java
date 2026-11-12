package com.upcraft.stay.billing.service;

import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Outcome returned by a payment gateway or manual-recording boundary.
 */
public record GatewayRecordingResult(
        String providerName,
        String gatewayPaymentId,
        PaymentStatus paymentStatus
) {}

