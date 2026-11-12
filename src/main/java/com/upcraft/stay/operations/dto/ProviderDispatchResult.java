package com.upcraft.stay.operations.dto;

import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;

/**
 * Honest outcome returned by an external notification provider adapter.
 */
public record ProviderDispatchResult(
        NotificationDeliveryStatus status,
        String providerName,
        String providerMessageId,
        String errorMessage
) {
}

