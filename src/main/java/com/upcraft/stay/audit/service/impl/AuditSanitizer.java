package com.upcraft.stay.audit.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Strips credentials, secrets, OTPs, and sensitive personal tokens before persisting audit metadata.
 */
@Component
public class AuditSanitizer {

    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_METADATA_VALUE_LENGTH = 160;
    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password", "secret", "token", "otp", "authorization", "auth",
            "credential", "card", "cvv", "pin", "apikey", "key", "phone", "mobile"
    );

    private final ObjectMapper objectMapper;

    public AuditSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Filters out disallowed metadata keys and serializes safe string attributes to JSON.
     *
     * @param rawMetadata candidate metadata key-value pairs
     * @return sanitized JSON string or {@code null} when empty
     */
    public String toSafeJson(Map<String, Object> rawMetadata) {
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return null;
        }
        Map<String, String> safeMap = new LinkedHashMap<>();
        rawMetadata.forEach((rawKey, rawValue) -> addIfSafe(safeMap, rawKey, rawValue));
        if (safeMap.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(safeMap);
    }

    /**
     * Deserializes persisted safe JSON metadata for API responses.
     *
     * @param metadataJson stored JSON column text
     * @return immutable map of safe key-value attributes
     */
    public Map<String, String> parseSafeJson(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(
                    metadataJson,
                    new TypeReference<Map<String, String>>() {
                    }
            );
            return Collections.unmodifiableMap(parsed);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    public String truncateSummary(String summary) {
        return StringUtils.left(StringUtils.defaultIfBlank(summary, "Audited event").trim(), MAX_SUMMARY_LENGTH);
    }

    private void addIfSafe(Map<String, String> safeMap, String rawKey, Object rawValue) {
        if (StringUtils.isBlank(rawKey) || Optional.ofNullable(rawValue).isEmpty()) {
            return;
        }
        String normalizedKey = rawKey.trim().toLowerCase(Locale.ROOT);
        boolean isSensitive = SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(normalizedKey::contains);
        if (!isSensitive) {
            safeMap.put(rawKey.trim(), StringUtils.left(rawValue.toString().trim(), MAX_METADATA_VALUE_LENGTH));
        }
    }
}

