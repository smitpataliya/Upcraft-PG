package com.upcraft.stay.operations.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.enums.NotificationChannel;
import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;
import com.upcraft.stay.operations.service.NotificationChannelProvider;

/**
 * Configuration boundary for SMS notification gateways.
 * Never claims delivery unless an external SMS provider and API key are configured.
 */
@Component
@RequiredArgsConstructor
public class SmsNotificationProvider implements NotificationChannelProvider {

    private final AppProperties appProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public ProviderDispatchResult send(NotificationDispatchCommand command) {
        AppProperties.Notifications config = appProperties.getNotifications();
        String providerName = StringUtils.isBlank(config.getSmsProvider()) ? "SMS_GATEWAY" : config.getSmsProvider();
        if (StringUtils.isBlank(config.getSmsProvider()) || StringUtils.isBlank(config.getSmsApiKey())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    providerName,
                    null,
                    "SMS provider credentials not configured"
            );
        }
        if (StringUtils.isBlank(command.recipientPhone())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    providerName,
                    null,
                    "Recipient phone missing"
            );
        }
        return new ProviderDispatchResult(
                NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                providerName,
                null,
                "SMS gateway adapter requires live provider integration"
        );
    }
}

