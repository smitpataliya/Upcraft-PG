package com.upcraft.stay.common.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 * Instance-local bounded Caffeine sliding-minute rate limiter protecting authentication,
 * public share links, file uploads, and export endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60L;
    private static final long MAX_TRACKED_KEYS = 10_000L;
    private static final Set<String> AUTH_PREFIXES = Set.of(
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
        return resolveBucket(request.getRequestURI()).isEmpty();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<RateBucket> bucketOpt = resolveBucket(request.getRequestURI());
        if (bucketOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        RateBucket bucket = bucketOpt.get();
        String clientIp = TrustedProxyIpResolver.resolveClientIp(request, appProperties.getRateLimit().getTrustedProxies());
        String key = String.format("%s:%s:%s", bucket.name(), clientIp, request.getRequestURI());
        AtomicInteger counter = requestCounts.get(key, ignoredKey -> new AtomicInteger(0));
        if (counter.incrementAndGet() > Math.max(1, bucket.limitPerMinute())) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<RateBucket> resolveBucket(String path) {
        if (AUTH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return Optional.of(new RateBucket("AUTH", appProperties.getRateLimit().getAuthRequestsPerMinute()));
        }
        if (path.startsWith("/api/v1/public/share")) {
            return Optional.of(new RateBucket("PUBLIC_SHARE", appProperties.getRateLimit().getPublicShareRequestsPerMinute()));
        }
        if (path.startsWith("/api/v1/files/upload")) {
            return Optional.of(new RateBucket("UPLOAD", appProperties.getRateLimit().getUploadRequestsPerMinute()));
        }
        if (path.startsWith("/api/v1/share-links") || (path.startsWith("/api/v1/reports/") && path.endsWith("/export"))) {
            return Optional.of(new RateBucket("SENSITIVE", appProperties.getRateLimit().getExportRequestsPerMinute()));
        }
        return Optional.empty();
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

    private record RateBucket(String name, int limitPerMinute) {
    }
}
