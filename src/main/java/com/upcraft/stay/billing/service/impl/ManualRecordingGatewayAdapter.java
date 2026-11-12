package com.upcraft.stay.billing.service.impl;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;
import com.upcraft.stay.billing.service.GatewayRecordingResult;
import com.upcraft.stay.billing.service.PaymentGatewayBoundary;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;
import com.upcraft.stay.common.exception.ValidationException;

/**
 * Default manual recording adapter enforcing provider-reference verification for gateway methods.
 */
@Component
public class ManualRecordingGatewayAdapter implements PaymentGatewayBoundary {

    private static final String MANUAL_PROVIDER = "MANUAL";
    private static final String EXTERNAL_GATEWAY_PROVIDER = "EXTERNAL_VERIFIED";

    @Override
    public GatewayRecordingResult verifyAndResolve(RecordPaymentRequestDTO paymentRequest) {
        if (paymentRequest.paymentMethod() == PaymentMethod.GATEWAY) {
            if (StringUtils.isBlank(paymentRequest.referenceNumber())) {
                throw new ValidationException(
                        "Gateway payment cannot be marked successful without a verified provider transaction reference"
                );
            }
            return new GatewayRecordingResult(
                    EXTERNAL_GATEWAY_PROVIDER,
                    paymentRequest.referenceNumber().trim(),
                    PaymentStatus.COMPLETED
            );
        }
        return new GatewayRecordingResult(
                MANUAL_PROVIDER,
                Optional.ofNullable(StringUtils.trimToNull(paymentRequest.referenceNumber())).orElse(null),
                PaymentStatus.COMPLETED
        );
    }
}

