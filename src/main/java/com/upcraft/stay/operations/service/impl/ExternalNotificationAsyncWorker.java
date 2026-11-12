package com.upcraft.stay.operations.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.entity.NotificationDeliveryEntity;
import com.upcraft.stay.operations.entity.NotificationPreferenceEntity;
import com.upcraft.stay.operations.enums.NotificationChannel;
import com.upcraft.stay.operations.repository.NotificationDeliveryRepository;
import com.upcraft.stay.operations.service.NotificationChannelProvider;
import com.upcraft.stay.organization.repository.OrganizationRepository;
import com.upcraft.stay.repository.NotificationRepository;
import com.upcraft.stay.repository.UserRepository;

/**
 * Asynchronous worker delivering enabled outbound channels and recording honest delivery outcomes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalNotificationAsyncWorker {

    private final List<NotificationChannelProvider> channelProviders;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Executes configured outbound providers without blocking the HTTP transaction thread.
     *
     * @param commands         dispatch payloads
     * @param preferenceByUser user preference snapshot map
     */
    @Async("notificationExecutor")
    @Transactional
    public void deliverAsync(
            List<NotificationDispatchCommand> commands,
            Map<UUID, NotificationPreferenceEntity> preferenceByUser
    ) {
        if (CollectionUtils.isEmpty(commands)) {
            return;
        }
        List<NotificationDeliveryEntity> deliveries = commands.stream()
                .flatMap(command -> channelProviders.stream()
                        .filter(provider -> isChannelEnabled(provider.channel(), preferenceByUser.get(command.recipientUserId())))
                        .map(provider -> buildDeliveryEntity(command, provider.channel(), provider.send(command))))
                .toList();

        if (!deliveries.isEmpty()) {
            notificationDeliveryRepository.saveAll(deliveries);
        }
    }

    private boolean isChannelEnabled(NotificationChannel channel, NotificationPreferenceEntity preference) {
        if (Optional.ofNullable(preference).isEmpty()) {
            return channel == NotificationChannel.PUSH || channel == NotificationChannel.EMAIL;
        }
        return switch (channel) {
            case IN_APP -> preference.isInAppEnabled();
            case PUSH -> preference.isPushEnabled();
            case EMAIL -> preference.isEmailEnabled();
            case SMS -> preference.isSmsEnabled();
            case WHATSAPP -> preference.isWhatsappEnabled();
        };
    }

    private NotificationDeliveryEntity buildDeliveryEntity(
            NotificationDispatchCommand command,
            NotificationChannel channel,
            ProviderDispatchResult result
    ) {
        NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
        entity.setOrganization(organizationRepository.getReferenceById(command.organizationId()));
        Optional.ofNullable(command.notificationId())
                .map(notificationRepository::getReferenceById)
                .ifPresent(entity::setNotification);
        entity.setUser(userRepository.getReferenceById(command.recipientUserId()));
        entity.setChannel(channel);
        entity.setDeliveryStatus(result.status());
        entity.setProviderName(result.providerName());
        entity.setProviderMessageId(result.providerMessageId());
        entity.setErrorMessage(result.errorMessage());
        entity.setAttemptedAt(Instant.now());
        return entity;
    }
}

