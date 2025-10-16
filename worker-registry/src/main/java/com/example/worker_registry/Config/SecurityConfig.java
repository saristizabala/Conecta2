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
            // CORS/CSRF
            .cors(cors -> { })       // Usa CorsConfig
            .csrf(csrf -> csrf.disable())

            // H2 console
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            // Manejo de errores de auth (401/403) en JSON simple
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

            .authorizeHttpRequests(auth -> auth
                // Preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // H2 console (dev)
                .requestMatchers("/h2-console/**").permitAll()

                // AUTH público
                .requestMatchers("/api/v1/auth/**").permitAll()

                // HU005/HU006: listado público de disponibles
                .requestMatchers(HttpMethod.GET, "/api/v1/clients/services/public/**").permitAll()

                // El resto de /clients/services requiere autenticación
                .requestMatchers("/api/v1/clients/services/**").authenticated()

                // Cualquier otro endpoint (ajústalo si tienes más API públicas)
                .anyRequest().authenticated()
            )

            // JWT stateless
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Filtro JWT antes del UsernamePasswordAuthenticationFilter
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
