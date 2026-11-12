package com.upcraft.stay.operations.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.operations.dto.PushTokenResponseDTO;
import com.upcraft.stay.operations.dto.RegisterPushTokenRequestDTO;
import com.upcraft.stay.operations.service.NotificationV1Service;

/**
 * Registers and deactivates mobile device push tokens for authenticated users.
 */
@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
public class PushTokenV1Controller {

    private final NotificationV1Service notificationV1Service;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<PushTokenResponseDTO>> register(
            Authentication authentication,
            @Valid @RequestBody RegisterPushTokenRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Push token registered",
                notificationV1Service.registerPushToken(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(
            Authentication authentication,
            @RequestParam String token
    ) {
        notificationV1Service.deactivatePushToken(authentication.getName(), token);
        return ResponseEntity.noContent().build();
    }
}

