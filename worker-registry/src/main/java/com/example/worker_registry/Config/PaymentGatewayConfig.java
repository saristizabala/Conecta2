package com.example.worker_registry.Config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.example.worker_registry.Services.payments.PaymentGatewayClient;

@Configuration
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class PaymentGatewayConfig {

    @Bean
    public RestClient paymentRestClient(PaymentGatewayProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toMillis(properties.getConnectTimeout()));
        factory.setReadTimeout(toMillis(properties.getReadTimeout()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("X-API-KEY", properties.getApiKey())
                .build();
    }

    @Bean
    public PaymentGatewayClient paymentGatewayClient(RestClient paymentRestClient,
                                                     PaymentGatewayProperties properties) {
        return new PaymentGatewayClient(paymentRestClient, properties);
    }

    private int toMillis(Duration duration) {
        if (duration == null) {
            return 0;
        }
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
