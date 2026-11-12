package com.upcraft.stay.operations.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.NotificationDeliveryResponseDTO;
import com.upcraft.stay.operations.dto.NotificationPreferenceResponseDTO;
import com.upcraft.stay.operations.dto.NotificationV1ResponseDTO;
import com.upcraft.stay.operations.dto.PushTokenResponseDTO;
import com.upcraft.stay.operations.dto.RegisterPushTokenRequestDTO;
import com.upcraft.stay.operations.dto.UnreadCountResponseDTO;
import com.upcraft.stay.operations.dto.UpdateNotificationPreferenceRequestDTO;

/**
 * Versioned v1 user notification inbox, preferences, push token, and delivery audit service.
 */
public interface NotificationV1Service {

    PageResponseDTO<NotificationV1ResponseDTO> listMyNotifications(
            String principalEmail,
            boolean unreadOnly,
            int page,
            Integer size
    );

    UnreadCountResponseDTO getUnreadCount(String principalEmail);

    void markAsRead(String principalEmail, UUID notificationId);

    void markAllAsRead(String principalEmail);

    NotificationPreferenceResponseDTO getPreferences(String principalEmail);

    NotificationPreferenceResponseDTO updatePreferences(
            String principalEmail,
            UpdateNotificationPreferenceRequestDTO request
    );

    PushTokenResponseDTO registerPushToken(String principalEmail, RegisterPushTokenRequestDTO request);

    void deactivatePushToken(String principalEmail, String token);

    PageResponseDTO<NotificationDeliveryResponseDTO> listDeliveries(String principalEmail, int page, Integer size);
}

