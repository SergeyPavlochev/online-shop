package ru.spavlochev.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Configuration
public class RestClientConfig {

    @Bean
    public ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest httpRequest = attributes.getRequest();
                    String correlationId = httpRequest.getHeader("X-Correlation-Id");
                    if (correlationId != null && !correlationId.isBlank()) {
                        request.getHeaders().set("X-Correlation-Id", correlationId);
                    }
                }
                return execution.execute(request, body);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    public ClientHttpRequestInterceptor jwtForwardInterceptor() {
        return (request, body, execution) -> {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                    request.getHeaders().setBearerAuth(jwt.getTokenValue());
                }
                return execution.execute(request, body);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    public ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            try {
                String method = request.getMethod().name();
                String uri = request.getURI().toString();

                log.info("Outgoing request: {} {}", method, uri);

                long start = System.currentTimeMillis();
                org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);

                int statusCode = response.getStatusCode().value();
                long duration = System.currentTimeMillis() - start;

                log.info("Outgoing response: {} {} -> {} ({}ms)", method, uri, statusCode, duration);

                return response;
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    public RestClient restClient(
            ClientHttpRequestInterceptor correlationIdInterceptor,
            ClientHttpRequestInterceptor jwtForwardInterceptor,
            ClientHttpRequestInterceptor loggingInterceptor) {
        return RestClient.builder()
                .requestInterceptor(correlationIdInterceptor)
                .requestInterceptor(jwtForwardInterceptor)
                .requestInterceptor(loggingInterceptor)
                .build();
    }
}
