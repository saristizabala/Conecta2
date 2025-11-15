package com.example.worker_registry.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public class PaymentGatewayProperties {

    /**
     * Base URL del microservicio PasarelaPagos.
     */
    private String baseUrl;

    /**
     * API key requerida por la pasarela (X-API-KEY).
     */
    private String apiKey;

    /**
     * Secreto esperado en el webhook entrante.
     */
    private String webhookSecret;

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(4);
    private int maxRetries = 3;
    private List<Integer> retryStatuses = new ArrayList<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<Integer> getRetryStatuses() {
        return retryStatuses;
    }

    public void setRetryStatuses(List<Integer> retryStatuses) {
        this.retryStatuses = retryStatuses;
    }
}
