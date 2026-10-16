package ru.spavlochev.billing.config;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(1)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlation_id", correlationId);
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 4096);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();

        log.info("Incoming request: {} {}{}", method, uri, queryString != null ? "?" + queryString : "");

        try {
            long start = System.currentTimeMillis();
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            long duration = System.currentTimeMillis() - start;

            String requestBody = new String(wrappedRequest.getContentAsByteArray());
            if (!requestBody.isEmpty()) {
                log.info("Request body: {}", requestBody);
            }

            String responseBody = new String(wrappedResponse.getContentAsByteArray());
            if (!responseBody.isEmpty()) {
                log.info("Response body: {}", responseBody);
            }

            log.info("Outgoing response: {} {}{} -> {} ({}ms)", method, uri,
                    queryString != null ? "?" + queryString : "", response.getStatus(), duration);

            wrappedResponse.copyBodyToResponse();
        } finally {
            MDC.remove("correlation_id");
        }
    }
}
