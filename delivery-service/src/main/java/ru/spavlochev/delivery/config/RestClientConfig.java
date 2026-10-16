package ru.spavlochev.delivery.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    public ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (HttpRequest httpRequest, byte[] body, ClientHttpRequestExecution execution) -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId != null && !correlationId.isBlank()) {
                    httpRequest.getHeaders().set("X-Correlation-Id", correlationId);
                }
            }
            return execution.execute(httpRequest, body);
        };
    }

    @Bean
    public ClientHttpRequestInterceptor jwtForwardInterceptor() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                request.getHeaders().setBearerAuth(jwt.getTokenValue());
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public ClientHttpRequestInterceptor loggingInterceptor() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            String method = request.getMethod().name();
            String uri = request.getURI().toString();

            log.info("Outgoing request: {} {}", method, uri);
            if (body.length > 0) {
                log.info("Request body: {}", new String(body, java.nio.charset.StandardCharsets.UTF_8));
            }

            long start = System.currentTimeMillis();
            ClientHttpResponse response = execution.execute(request, body);

            int statusCode = response.getStatusCode().value();
            long duration = System.currentTimeMillis() - start;

            log.info("Outgoing response: {} {} -> {} ({}ms)", method, uri, statusCode, duration);

            return response;
        };
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestInterceptors(interceptors ->
                        interceptors.addAll(List.of(correlationIdInterceptor(), loggingInterceptor(), jwtForwardInterceptor())))
                .build();
    }
}
