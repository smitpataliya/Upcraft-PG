package com.upcraft.stay.common.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.upcraft.stay.common.constants.AppConstants;

/**
 * Assigns or propagates a correlation trace identifier across MDC logs and HTTP responses.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(AppConstants.TRACE_ID_HEADER);
        String traceId = StringUtils.isNotBlank(incomingTraceId)
                ? StringUtils.left(incomingTraceId.trim(), 64)
                : UUID.randomUUID().toString();
        MDC.put(AppConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(AppConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(AppConstants.TRACE_ID_MDC_KEY);
        }
    }
}

