package com.upcraft.stay.common.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Bounded per-IP sliding-minute rate limiter protecting authentication and OTP endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60L;
    private static final long MAX_TRACKED_KEYS = 10_000L;
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/reset-password",
            "/api/v1/invitations/accept",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/verify-otp",
            "/api/auth/reset-password"
    );

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Cache<String, AtomicInteger> requestCounts;

    /**
     * Initializes bounded Caffeine window counter cache.
     *
     * @param appProperties typed app configuration
     * @param objectMapper  JSON serializer for 429 payloads
     */
    public RateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.requestCounts = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return PROTECTED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String key = resolveClientKey(request) + ":" + request.getRequestURI();
        AtomicInteger counter = requestCounts.get(key, ignoredKey -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();
        int maxAllowed = Math.max(1, appProperties.getRateLimit().getAuthRequestsPerMinute());
        if (currentCount > maxAllowed) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.substringBefore(forwardedFor, ",").trim();
        }
        return StringUtils.defaultIfBlank(request.getRemoteAddr(), "unknown");
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        String traceId = MDC.get(AppConstants.TRACE_ID_MDC_KEY);
        ApiResponseDTO<Void> body = ApiResponseDTO.error(
                "Too many requests. Please try again shortly.",
                ErrorCode.RATE_LIMIT_EXCEEDED,
                traceId
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
