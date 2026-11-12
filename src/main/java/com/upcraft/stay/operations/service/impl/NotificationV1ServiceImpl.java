package com.upcraft.stay.operations.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Notification;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.operations.dto.NotificationDeliveryResponseDTO;
import com.upcraft.stay.operations.dto.NotificationPreferenceResponseDTO;
import com.upcraft.stay.operations.dto.NotificationV1ResponseDTO;
import com.upcraft.stay.operations.dto.PushTokenResponseDTO;
import com.upcraft.stay.operations.dto.RegisterPushTokenRequestDTO;
import com.upcraft.stay.operations.dto.UnreadCountResponseDTO;
import com.upcraft.stay.operations.dto.UpdateNotificationPreferenceRequestDTO;
import com.upcraft.stay.operations.entity.NotificationPreferenceEntity;
import com.upcraft.stay.operations.entity.PushTokenEntity;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.repository.NotificationDeliveryRepository;
import com.upcraft.stay.operations.repository.NotificationPreferenceRepository;
import com.upcraft.stay.operations.repository.PushTokenRepository;
import com.upcraft.stay.operations.service.NotificationV1Service;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.repository.NotificationRepository;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Manages paginated notifications, preferences, and mobile push tokens.
 */
@Service
@RequiredArgsConstructor
public class NotificationV1ServiceImpl implements NotificationV1Service {

    private static final Set<String> NOTIFICATION_SORT_FIELDS = Set.of("createdAt", "isRead");

    private final AccessGuardService accessGuardService;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PushTokenRepository pushTokenRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<NotificationV1ResponseDTO> listMyNotifications(
            String principalEmail,
            boolean unreadOnly,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", NOTIFICATION_SORT_FIELDS, "createdAt", appProperties
        );
        Page<Notification> resultPage = unreadOnly
                ? notificationRepository.findAllByUserIdAndIsReadFalse(currentUser.getId(), pageable)
                : notificationRepository.findAllByUserId(currentUser.getId(), pageable);
        return PageResponseDTO.from(resultPage.map(operationsMapper::toNotificationResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDTO getUnreadCount(String principalEmail) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        return new UnreadCountResponseDTO(notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId()));
    }

    @Override
    @Transactional
    public void markAsRead(String principalEmail, UUID notificationId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Cannot mark another user's notification as read");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(String principalEmail) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Instant now = Instant.now();
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .filter(notification -> !notification.isRead())
                .peek(notification -> {
                    notification.setRead(true);
                    notification.setReadAt(now);
                })
                .toList();
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponseDTO getPreferences(String principalEmail) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        return notificationPreferenceRepository.findByUserId(currentUser.getId())
                .map(operationsMapper::toPreferenceResponse)
                .orElse(new NotificationPreferenceResponseDTO(true, true, true, false, false, null));
    }

    @Override
    @Transactional
    public NotificationPreferenceResponseDTO updatePreferences(
            String principalEmail,
            UpdateNotificationPreferenceRequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        NotificationPreferenceEntity entity = notificationPreferenceRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> {
                    NotificationPreferenceEntity created = new NotificationPreferenceEntity();
                    created.setOrganization(organization);
                    created.setUser(currentUser);
                    return created;
                });
        entity.setInAppEnabled(request.inAppEnabled());
        entity.setPushEnabled(request.pushEnabled());
        entity.setEmailEnabled(request.emailEnabled());
        entity.setSmsEnabled(request.smsEnabled());
        entity.setWhatsappEnabled(request.whatsappEnabled());
        entity.setDigestQuietHours(request.digestQuietHours());
        return operationsMapper.toPreferenceResponse(notificationPreferenceRepository.save(entity));
    }

    @Override
    @Transactional
    public PushTokenResponseDTO registerPushToken(String principalEmail, RegisterPushTokenRequestDTO request) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        String trimmedToken = request.token().trim();
        PushTokenEntity entity = pushTokenRepository.findByToken(trimmedToken)
                .orElseGet(PushTokenEntity::new);
        entity.setOrganization(organization);
        entity.setUser(currentUser);
        entity.setToken(trimmedToken);
        entity.setPlatform(request.platform().trim().toUpperCase());
        entity.setDeviceName(request.deviceName());
        entity.setActive(true);
        entity.setLastSeenAt(Instant.now());
        return operationsMapper.toPushTokenResponse(pushTokenRepository.save(entity));
    }

    @Override
    @Transactional
    public void deactivatePushToken(String principalEmail, String token) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        pushTokenRepository.findByToken(token.trim())
                .filter(existing -> existing.getUser().getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    existing.setActive(false);
                    pushTokenRepository.save(existing);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<NotificationDeliveryResponseDTO> listDeliveries(
            String principalEmail,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "attemptedAt", "DESC", Set.of("attemptedAt"), "attemptedAt", appProperties
        );
        return PageResponseDTO.from(
                notificationDeliveryRepository
                        .findAllByOrganizationIdOrderByAttemptedAtDesc(organization.getId(), pageable)
                        .map(operationsMapper::toDeliveryResponse)
        );
    }
}

