package com.upcraft.stay.operations;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.service.impl.EmailNotificationProvider;
import com.upcraft.stay.operations.service.impl.ExpoPushNotificationProvider;
import com.upcraft.stay.operations.service.impl.SmsNotificationProvider;
import com.upcraft.stay.operations.service.impl.WhatsAppNotificationProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests verifying honest SKIPPED_UNCONFIGURED provider behavior when credentials/tokens are absent.
 */
class OperationsServiceUnitTest {

    @Test
    void providers_whenCredentialsOrTokensMissing_returnSkippedUnconfigured() {
        AppProperties appProperties = new AppProperties();
        NotificationDispatchCommand command = new NotificationDispatchCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant@upcraft.test",
                "9876543210",
                NotificationEventType.COMPLAINT_UPDATED,
                "Status Updated",
                "Plumber assigned",
                "COMPLAINT",
                UUID.randomUUID(),
                List.of()
        );

        ExpoPushNotificationProvider expoProvider = new ExpoPushNotificationProvider(appProperties, new ObjectMapper());
        EmailNotificationProvider emailProvider = new EmailNotificationProvider(appProperties, mock(JavaMailSender.class));
        SmsNotificationProvider smsProvider = new SmsNotificationProvider(appProperties);
        WhatsAppNotificationProvider whatsappProvider = new WhatsAppNotificationProvider(appProperties);

        ProviderDispatchResult pushResult = expoProvider.send(command);
        ProviderDispatchResult emailResult = emailProvider.send(command);
        ProviderDispatchResult smsResult = smsProvider.send(command);
        ProviderDispatchResult waResult = whatsappProvider.send(command);

        assertThat(pushResult.status()).isEqualTo(NotificationDeliveryStatus.SKIPPED_UNCONFIGURED);
        assertThat(emailResult.status()).isEqualTo(NotificationDeliveryStatus.SKIPPED_UNCONFIGURED);
        assertThat(smsResult.status()).isEqualTo(NotificationDeliveryStatus.SKIPPED_UNCONFIGURED);
        assertThat(waResult.status()).isEqualTo(NotificationDeliveryStatus.SKIPPED_UNCONFIGURED);
    }
}

