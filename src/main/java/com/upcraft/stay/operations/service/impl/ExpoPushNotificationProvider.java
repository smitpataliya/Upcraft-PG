package com.upcraft.stay.operations.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.enums.NotificationChannel;
import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;
import com.upcraft.stay.operations.service.NotificationChannelProvider;

/**
 * Dispatches mobile push notifications through the Expo Push HTTP API when enabled and tokens exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpoPushNotificationProvider implements NotificationChannelProvider {

    private static final String PROVIDER_NAME = "EXPO_PUSH";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public ProviderDispatchResult send(NotificationDispatchCommand command) {
        AppProperties.Notifications config = appProperties.getNotifications();
        if (!config.isPushEnabled() || StringUtils.isBlank(config.getExpoPushUrl())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    PROVIDER_NAME,
                    null,
                    "Expo push delivery disabled or URL missing"
            );
        }
        if (CollectionUtils.isEmpty(command.pushTokens())) {
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.SKIPPED_UNCONFIGURED,
                    PROVIDER_NAME,
                    null,
                    "No active device push tokens registered for recipient"
            );
        }

        try {
            List<Map<String, Object>> messages = command.pushTokens().stream()
                    .map(token -> Map.<String, Object>of(
                            "to", token,
                            "title", command.title(),
                            "body", command.message(),
                            "sound", "default"
                    ))
                    .toList();
            String bodyJson = objectMapper.writeValueAsString(messages);
            Duration timeout = Duration.ofMillis(Math.max(1000, config.getProviderTimeoutMs()));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getExpoPushUrl()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (StringUtils.isNotBlank(config.getExpoAccessToken())) {
                requestBuilder.header("Authorization", "Bearer " + config.getExpoAccessToken());
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<String> response = client.send(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ProviderDispatchResult(
                        NotificationDeliveryStatus.SENT,
                        PROVIDER_NAME,
                        "expo-http-" + response.statusCode(),
                        null
                );
            }
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.FAILED,
                    PROVIDER_NAME,
                    null,
                    "Expo push returned HTTP " + response.statusCode()
            );
        } catch (Exception exception) {
            log.warn("Expo push delivery failed. recipientUserId={}", command.recipientUserId(), exception);
            return new ProviderDispatchResult(
                    NotificationDeliveryStatus.FAILED,
                    PROVIDER_NAME,
                    null,
                    StringUtils.left(exception.getMessage(), 450)
            );
        }
    }
}
