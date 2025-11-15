package com.example.worker_registry.Services.payments;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.worker_registry.Config.PaymentGatewayProperties;

public class PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final RestClient restClient;
    private final PaymentGatewayProperties properties;

    public PaymentGatewayClient(RestClient restClient, PaymentGatewayProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public PaymentIntentResponse createIntent(PaymentIntentRequest request) {
        return executeWithRetry(() -> restClient.post()
                .uri("/payments/intents")
                .body(request)
                .retrieve()
                .body(PaymentIntentResponse.class));
    }

    public PaymentIntentResponse confirmIntent(String intentId, ConfirmPaymentIntentRequest request) {
        return executeWithRetry(() -> restClient.post()
                .uri("/payments/intents/{id}/confirm", intentId)
                .body(request)
                .retrieve()
                .body(PaymentIntentResponse.class));
    }

    public PaymentIntentResponse retrieveIntent(String intentId) {
        return executeWithRetry(() -> restClient.get()
                .uri("/payments/intents/{id}", intentId)
                .retrieve()
                .body(PaymentIntentResponse.class));
    }

    private <T> T executeWithRetry(Supplier<T> supplier) {
        int maxRetries = Math.max(1, properties.getMaxRetries());
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return supplier.get();
            } catch (RestClientResponseException ex) {
                lastException = ex;
                if (!shouldRetry(ex.getStatusCode()) || attempt == maxRetries) {
                    throw ex;
                }
                log.warn("[PaymentGateway] Status {} en intento {}. Reintentando...", ex.getRawStatusCode(), attempt);
            } catch (ResourceAccessException ex) {
                lastException = ex;
                if (attempt == maxRetries) {
                    throw ex;
                }
                log.warn("[PaymentGateway] Error de I/O en intento {}. Reintentando...", attempt, ex);
            }
            sleepBackoff(attempt);
        }
        throw lastException != null ? lastException : new IllegalStateException("Fallo desconocido contra pasarela");
    }

    private boolean shouldRetry(@Nullable HttpStatusCode statusCode) {
        if (statusCode == null) {
            return true;
        }
        Set<Integer> retryable = Set.copyOf(properties.getRetryStatuses());
        return retryable.contains(statusCode.value());
    }

    private void sleepBackoff(int attempt) {
        long millis = Math.min(500L, 100L * attempt);
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public record PaymentIntentRequest(
            String externalRef,
            BigDecimal amount,
            String currency,
            String description,
            Map<String, Object> metadata) {
    }

    public record ConfirmPaymentIntentRequest(
            String paymentMethod,
            Map<String, Object> metadata) {
    }

    public record PaymentIntentResponse(
            String id,
            String status,
            String clientSecret,
            String externalRef,
            BigDecimal amount,
            String currency,
            Map<String, Object> metadata) {

        public Map<String, Object> safeMetadata() {
            return metadata == null ? Collections.emptyMap() : metadata;
        }
    }
}
