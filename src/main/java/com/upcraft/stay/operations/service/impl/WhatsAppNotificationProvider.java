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
 * Configuration boundary for WhatsApp Business messaging providers.
 * Never claims delivery unless provider credentials are configured.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppNotificationProvider implements NotificationChannelProvider {

    private final AppProperties appProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public ProviderDispatchResult send(NotificationDispatchCommand command) {
        AppProperties.Notifications config = appProperties.getNotifications();
        String providerName = StringUtils.isBlank(config.getWhatsappProvider())
                ? "WHATSAPP_GATEWAY"
                : config.getWhatsappProvider();
        if (StringUtils.isBlank(config.getWhatsappProvider()) || StringUtils.isBlank(config.getWhatsappApiKey())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    providerName,
                    null,
                    "WhatsApp provider credentials not configured"
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
                "WhatsApp gateway adapter requires live provider integration"
        );
    }
}

