package ru.spavlochev.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${app.billing.base-url}")
    private String billingBaseUrl;
    @Value("${app.warehouse.base-url}")
    private String warehouseBaseUrl;
    @Value("${app.delivery.base-url}")
    private String deliveryBaseUrl;
    @Value("${KEYCLOAK_TOKEN_URI}")
    private String keycloakTokenUri;

    private static HttpClient createHttpClient() {
        return HttpClient.create(ConnectionProvider.builder("saga-connection-pool")
                        .maxConnections(50)
                        .build())
                .compress(true);
    }

    @Bean
    ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    var httpRequest = attributes.getRequest();
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
    ClientHttpRequestInterceptor jwtForwardInterceptor() {
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
    ClientHttpRequestInterceptor loggingInterceptor() {
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

    private ExchangeFilterFunction loggingFilter() {
        return (clientRequest, next) -> {
            log.info("Outgoing request: {} {}", clientRequest.method(), clientRequest.url());
            return next.exchange(clientRequest).map(clientResponse -> {
                log.info("Outgoing response: {} {} -> {}",
                        clientRequest.method(), clientRequest.url(),
                        clientResponse.statusCode().value());
                return clientResponse;
            });
        };
    }

    private ExchangeFilterFunction jwtForwardFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                ClientRequest authenticatedRequest = ClientRequest.from(clientRequest)
                        .header("Authorization", "Bearer " + jwt.getTokenValue())
                        .build();
                return Mono.just(authenticatedRequest);
            }
            return Mono.just(clientRequest);
        });
    }

    @Bean
    public WebClient billingWebClient() {
        return WebClient.builder()
                .baseUrl(billingBaseUrl)
                .filter(jwtForwardFilter())
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public WebClient warehouseWebClient() {
        return WebClient.builder()
                .baseUrl(warehouseBaseUrl)
                .filter(jwtForwardFilter())
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public WebClient deliveryWebClient() {
        return WebClient.builder()
                .baseUrl(deliveryBaseUrl)
                .filter(jwtForwardFilter())
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public WebClient m2mDeliveryWebClient() {
        return WebClient.builder()
                .baseUrl(deliveryBaseUrl)
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public WebClient m2mWarehouseWebClient() {
        return WebClient.builder()
                .baseUrl(warehouseBaseUrl)
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public WebClient m2mBillingWebClient() {
        return WebClient.builder()
                .baseUrl(billingBaseUrl)
                .filter(loggingFilter())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    @Bean
    public RestClient keycloakRestClient() {
        return RestClient.builder()
                .baseUrl(keycloakTokenUri)
                .build();
    }
}
