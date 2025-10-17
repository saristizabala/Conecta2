package com.example.worker_registry.Config;

import com.example.worker_registry.securtity.JwtAuthFilter;
import com.example.worker_registry.securtity.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ======================================================
            // CONFIGURACIÓN BÁSICA
            // ======================================================
            .cors(cors -> { }) // usa tu CorsConfig global
            .csrf(csrf -> csrf.disable())
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            // ======================================================
            // MANEJO DE EXCEPCIONES (RESPUESTA JSON)
            // ======================================================
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":401,\"mensaje\":\"No autorizado\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":403,\"mensaje\":\"Acceso prohibido\"}");
                })
            )

            // ======================================================
            // AUTORIZACIÓN DE RUTAS
            // ======================================================
            .authorizeHttpRequests(auth -> auth
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // AUTH público
                .requestMatchers("/api/v1/auth/**").permitAll()

                // HU005/HU006: listado público
                .requestMatchers(HttpMethod.GET, "/api/v1/clients/services/public/**").permitAll()

                // Rutas protegidas (CLIENT debe estar autenticado)
                .requestMatchers("/api/v1/clients/services/**").authenticated()

                // Por defecto, todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )

            // ======================================================
            // SESIONES Y FILTROS
            // ======================================================
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
