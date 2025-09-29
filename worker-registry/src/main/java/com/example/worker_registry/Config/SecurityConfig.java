package com.example.worker_registry.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // H2 console + error
                .requestMatchers("/h2-console/**", "/error").permitAll()

                // Favicon y estáticos (por si el navegador los pide desde el backend)
                .requestMatchers(
                    "/",                 // raíz
                    "/favicon.ico",      // ícono
                    "/index.html",
                    "/webjars/**",
                    "/static/**", "/public/**", "/resources/**", "/META-INF/resources/**",
                    "/assets/**", "/css/**", "/js/**", "/images/**"
                ).permitAll()

                // Endpoints públicos de auth (workers + clients)
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Resto: autenticado
                .anyRequest().authenticated()
            )
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable());

        return http.build();
    }
}
