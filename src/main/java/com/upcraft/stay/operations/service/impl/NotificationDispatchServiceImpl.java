package com.upcraft.stay.operations.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.upcraft.stay.domain.entity.Notification;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.entity.NotificationPreferenceEntity;
import com.upcraft.stay.operations.entity.PushTokenEntity;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.repository.NotificationPreferenceRepository;
import com.upcraft.stay.operations.repository.PushTokenRepository;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.NotificationRepository;

/**
 * Coordinates transactional in-app notification persistence and post-commit async provider dispatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ExternalNotificationAsyncWorker externalNotificationAsyncWorker;

    @Override
    @Transactional
    public void dispatchToUsers(
            OrganizationEntity organization,
            PropertyEntity property,
            Collection<User> recipients,
            NotificationEventType eventType,
            String title,
            String message,
            String referenceType,
            UUID referenceId
    ) {
        if (CollectionUtils.isEmpty(recipients) || Optional.ofNullable(organization).isEmpty()) {
            return;
        }
        List<User> activeUsers = recipients.stream()
                .filter(User::isActive)
                .distinct()
                .toList();
        if (activeUsers.isEmpty()) {
            return;
        }

        List<UUID> recipientIds = activeUsers.stream().map(User::getId).toList();
        Map<UUID, NotificationPreferenceEntity> preferenceByUser = notificationPreferenceRepository
                .findAllByUserIdIn(recipientIds).stream()
                .collect(Collectors.toMap(pref -> pref.getUser().getId(), Function.identity(), (first, second) -> first));

        Map<UUID, List<String>> tokensByUser = pushTokenRepository.findAllByUserIdInAndActiveTrue(recipientIds).stream()
                .collect(Collectors.groupingBy(
                        token -> token.getUser().getId(),
                        Collectors.mapping(PushTokenEntity::getToken, Collectors.toList())
                ));

        List<Notification> toPersist = activeUsers.stream()
                .filter(user -> Optional.ofNullable(preferenceByUser.get(user.getId()))
                        .map(NotificationPreferenceEntity::isInAppEnabled)
                        .orElse(true))
                .map(user -> buildInAppNotification(organization, property, user, eventType, title, message, referenceType, referenceId))
                .toList();

        List<Notification> savedNotifications = toPersist.isEmpty()
                ? List.of()
                : notificationRepository.saveAll(toPersist);

        Map<UUID, UUID> notificationIdByUser = savedNotifications.stream()
                .collect(Collectors.toMap(item -> item.getUser().getId(), Notification::getId, (first, second) -> first));

        List<NotificationDispatchCommand> commands = activeUsers.stream()
                .map(user -> new NotificationDispatchCommand(
                        organization.getId(),
                        notificationIdByUser.get(user.getId()),
                        user.getId(),
                        user.getEmail(),
                        user.getPhone(),
                        eventType,
                        title,
                        message,
                        referenceType,
                        referenceId,
                        tokensByUser.getOrDefault(user.getId(), List.of())
                ))
                .toList();

        scheduleAfterCommit(commands, preferenceByUser);
        log.info("Queued notifications. orgId={}, eventType={}, recipients={}",
                organization.getId(), eventType, activeUsers.size());
    }

    private Notification buildInAppNotification(
            OrganizationEntity organization,
            PropertyEntity property,
            User user,
            NotificationEventType eventType,
            String title,
            String message,
            String referenceType,
            UUID referenceId
    ) {
        return Notification.builder()
                .organization(organization)
                .property(property)
                .user(user)
                .title(title)
                .message(message)
                .type(eventType.name())
                .eventType(eventType)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .isRead(false)
                .build();
    }

    private void scheduleAfterCommit(
            List<NotificationDispatchCommand> commands,
            Map<UUID, NotificationPreferenceEntity> preferenceByUser
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    externalNotificationAsyncWorker.deliverAsync(commands, preferenceByUser);
                }
            });
            return;
        }
        externalNotificationAsyncWorker.deliverAsync(commands, preferenceByUser);
    }
}

