package ru.spavlochev.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${app.billing.base-url}")
    private String billingBaseUrl;
    @Value("${app.warehouse.base-url}")
    private String warehouseBaseUrl;
    @Value("${app.delivery.base-url}")
    private String deliveryBaseUrl;

    private ExchangeFilterFunction jwtForwardFilter() {
        return (clientRequest, next) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                ClientRequest modifiedRequest = ClientRequest.from(clientRequest)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .build();
                return next.exchange(modifiedRequest);
            }
            return next.exchange(clientRequest);
        };
    }

    private ExchangeFilterFunction correlationIdFilter() {
        return (clientRequest, next) -> {
            String correlationId = clientRequest.headers().getFirst("X-Correlation-Id");
            if (correlationId != null && !correlationId.isBlank()) {
                ClientRequest modifiedRequest = ClientRequest.from(clientRequest)
                    .header("X-Correlation-Id", correlationId)
                    .build();
                return next.exchange(modifiedRequest);
            }
            return next.exchange(clientRequest);
        };
    }

    private ExchangeFilterFunction loggingFilter() {
        return (clientRequest, next) -> {
            String method = clientRequest.method().name();
            String uri = clientRequest.url().toString();

            log.info("Outgoing request: {} {}", method, uri);

            long start = System.currentTimeMillis();
            Mono<ClientResponse> responseMono = next.exchange(clientRequest);

            return responseMono.map(response -> {
                long duration = System.currentTimeMillis() - start;
                log.info("Outgoing response: {} {} -> {} ({}ms)", method, uri, response.statusCode().value(), duration);
                return response;
            });
        };
    }

    @Bean
    public WebClient billingWebClient() {
        return WebClient.builder()
                .baseUrl(billingBaseUrl)
                .filter(jwtForwardFilter())
                .filter(correlationIdFilter())
                .filter(loggingFilter())
                .build();
    }

    @Bean
    public WebClient warehouseWebClient() {
        return WebClient.builder()
                .baseUrl(warehouseBaseUrl)
                .filter(jwtForwardFilter())
                .filter(correlationIdFilter())
                .filter(loggingFilter())
                .build();
    }

    @Bean
    public WebClient deliveryWebClient() {
        return WebClient.builder()
                .baseUrl(deliveryBaseUrl)
                .filter(jwtForwardFilter())
                .filter(correlationIdFilter())
                .filter(loggingFilter())
                .build();
    }
}
