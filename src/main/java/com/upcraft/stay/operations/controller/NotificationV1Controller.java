package com.upcraft.stay.operations.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.NotificationDeliveryResponseDTO;
import com.upcraft.stay.operations.dto.NotificationPreferenceResponseDTO;
import com.upcraft.stay.operations.dto.NotificationV1ResponseDTO;
import com.upcraft.stay.operations.dto.UnreadCountResponseDTO;
import com.upcraft.stay.operations.dto.UpdateNotificationPreferenceRequestDTO;
import com.upcraft.stay.operations.service.NotificationV1Service;

/**
 * Versioned v1 endpoints for notification inbox, channel preferences, and delivery audit logs.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationV1Controller {

    private final NotificationV1Service notificationV1Service;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<NotificationV1ResponseDTO>>> listNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Notifications loaded",
                notificationV1Service.listMyNotifications(authentication.getName(), unreadOnly, page, size),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponseDTO<UnreadCountResponseDTO>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Unread notification count fetched",
                notificationV1Service.getUnreadCount(authentication.getName()),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponseDTO<Void>> markRead(
            Authentication authentication,
            @PathVariable UUID notificationId
    ) {
        notificationV1Service.markAsRead(authentication.getName(), notificationId);
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Notification marked read",
                null,
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponseDTO<Void>> markAllRead(Authentication authentication) {
        notificationV1Service.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(ApiResponseDTO.success(
                "All notifications marked read",
                null,
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponseDTO<NotificationPreferenceResponseDTO>> getPreferences(
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Notification preferences loaded",
                notificationV1Service.getPreferences(authentication.getName()),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponseDTO<NotificationPreferenceResponseDTO>> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateNotificationPreferenceRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Notification preferences updated",
                notificationV1Service.updatePreferences(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<NotificationDeliveryResponseDTO>>> listDeliveries(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Notification delivery audit loaded",
                notificationV1Service.listDeliveries(authentication.getName(), page, size),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }
}

