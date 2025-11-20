package com.vuong.simplerest.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(0) // Execute before other filters
public class LoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String METHOD_KEY = "method";
    private static final String URI_KEY = "uri";
    private static final String USER_AGENT_KEY = "userAgent";
    private static final String REMOTE_ADDR_KEY = "remoteAddr";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Generate or extract request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        // Generate or extract correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = requestId; // Use request ID as correlation ID if not provided
        }

        // Add to MDC for structured logging
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(METHOD_KEY, request.getMethod());
        MDC.put(URI_KEY, request.getRequestURI());
        MDC.put(USER_AGENT_KEY, request.getHeader("User-Agent"));
        MDC.put(REMOTE_ADDR_KEY, request.getRemoteAddr());

        // Add to response headers
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove(METHOD_KEY);
            MDC.remove(URI_KEY);
            MDC.remove(USER_AGENT_KEY);
            MDC.remove(REMOTE_ADDR_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filtering for actuator and static resources
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/") ||
               uri.startsWith("/swagger-ui/") ||
               uri.startsWith("/v3/api-docs/") ||
               uri.endsWith(".ico") ||
               uri.endsWith(".css") ||
               uri.endsWith(".js") ||
               uri.endsWith(".png") ||
               uri.endsWith(".jpg") ||
               uri.endsWith(".jpeg") ||
               uri.endsWith(".gif");
    }
}
