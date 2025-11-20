package com.vuong.simplerest.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Filter for sanitizing HTTP request parameters to prevent security vulnerabilities.
 * Uses InputSanitizer to clean query parameters and request attributes.
 * Executes after LoggingFilter to ensure request context is available.
 */
@Component
@Order(1)
public class RequestSanitizationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestSanitizationFilter.class);

    @Autowired
    private InputSanitizer inputSanitizer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Sanitize query parameters
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            for (String value : entry.getValue()) {
                if (inputSanitizer.containsMaliciousContent(value)) {
                    logger.warn("Malicious content detected in parameter {}: {}",
                        inputSanitizer.sanitizeForLogging(entry.getKey()),
                        inputSanitizer.sanitizeForLogging(value));
                    httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request content");
                    return;
                }
            }
        }

        // Sanitize headers (basic check)
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent != null && inputSanitizer.containsMaliciousContent(userAgent)) {
            logger.warn("Malicious content detected in User-Agent header");
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request content");
            return;
        }

        // Log sanitized request for debugging
        if (logger.isDebugEnabled()) {
            logger.debug("Processing request: {} {}",
                httpRequest.getMethod(),
                inputSanitizer.sanitizeForLogging(httpRequest.getRequestURI()));
        }

        chain.doFilter(request, response);
    }
}
