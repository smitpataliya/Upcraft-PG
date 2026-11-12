package com.upcraft.stay.operations.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.enums.NotificationChannel;
import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;
import com.upcraft.stay.operations.service.NotificationChannelProvider;

/**
 * Dispatches notification emails via {@link JavaMailSender} only when SMTP credentials are configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationProvider implements NotificationChannelProvider {

    private static final String PROVIDER_NAME = "SPRING_SMTP";

    private final AppProperties appProperties;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderDispatchResult send(NotificationDispatchCommand command) {
        if (!appProperties.getNotifications().isEmailEnabled() || StringUtils.isBlank(smtpUsername)) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    PROVIDER_NAME,
                    null,
                    "Email notifications disabled or SMTP username unconfigured"
            );
        }
        if (StringUtils.isBlank(command.recipientEmail())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    PROVIDER_NAME,
                    null,
                    "Recipient email address missing"
            );
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(command.recipientEmail());
            message.setSubject(command.title());
            message.setText(command.message());
            mailSender.send(message);
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SENT,
                    PROVIDER_NAME,
                    "smtp-sent",
                    null
            );
        } catch (Exception exception) {
            log.warn("SMTP notification failed. recipientUserId={}", command.recipientUserId(), exception);
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.FAILED,
                    PROVIDER_NAME,
                    null,
                    StringUtils.left(exception.getMessage(), 450)
            );
        }
    }
}

