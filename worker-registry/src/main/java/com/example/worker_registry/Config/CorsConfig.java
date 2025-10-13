package com.example.worker_registry.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();

        // HU005/HU006: ADDED - Permitir front local (Flutter Web)
        cfg.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:4200",
                "http://localhost:5173",
                "http://localhost:5000",
                "http://localhost:50063", // Flutter web usa puertos aleatorios: agrega los tuyos
                "http://localhost:53977",
                "http://localhost:58435",
                "http://localhost:62270",
                "http://127.0.0.1:50063",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:8080" // si pruebas desde navegador contra swagger u otros
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setExposedHeaders(List.of("Authorization", "Location"));
        cfg.setAllowCredentials(false); // si vas a enviar cookies, cámbialo a true

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
