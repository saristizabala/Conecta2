package com.example.worker_registry.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración CORS global
 * Compatible con Flutter Web (localhost, 127.0.0.1, 10.0.2.2)
 * Expone encabezados "Authorization" y "Content-Type"
 * y permite credenciales y métodos usados en JWT + REST API.
 */
@Configuration
public class CorsConfig {

    // Permite configurarlo desde application.properties:
    // app.cors.allowed-origins=http://localhost:*,http://127.0.0.1:*,http://10.0.2.2:*
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://10.0.2.2:*}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Orígenes permitidos
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .forEach(cfg::addAllowedOriginPattern);

        // Métodos y encabezados
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
