package com.example.worker_registry.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // HU005/HU006: ADDED - CORS y CSRF
            .cors(cors -> {}) // Usa el CorsFilter definido en CorsConfig
            .csrf(csrf -> csrf.disable())

            // H2 console en <frame>
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            .authorizeHttpRequests(auth -> auth
                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // H2 Console (dev)
                .requestMatchers("/h2-console/**").permitAll()

                // ====== AUTH público existente ======
                .requestMatchers(
                    "/api/v1/auth/**"
                ).permitAll()

                // ====== HU005/HU006 Servicios ======
                // Listar y ver detalle disponibles (trabajador/cliente)
                .requestMatchers(HttpMethod.GET, "/api/v1/services/**").permitAll()

                // Crear/editar/eliminar (cliente autenticado; cuando agregues roles: hasRole("CLIENT"))
                .requestMatchers(HttpMethod.POST, "/api/v1/services/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/services/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/services/**").authenticated()

                // Todo lo demás requiere autenticación (si hay endpoints protegidos adicionales)
                .anyRequest().authenticated()
            )

            // Stateless (para JWT)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Nota: cuando agregues tu filtro JWT, aquí se encadena con addFilterBefore(...)
        return http.build();
    }
}
