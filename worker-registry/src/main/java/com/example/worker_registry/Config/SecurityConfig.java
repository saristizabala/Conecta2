package com.example.worker_registry.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS + CSRF
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())

            // H2 console en <frame>
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            // Autorización
            .authorizeHttpRequests(auth -> auth
                // H2 console
                .requestMatchers("/h2-console/**").permitAll()

                // Endpoints públicos de autenticación
                .requestMatchers(
                    "/api/v1/auth/workers/register",
                    "/api/v1/auth/verify",
                    "/api/v1/auth/login",
                    "/api/v1/auth/resend-activation"
                ).permitAll()

                // Todo lo demás, autenticado (cuando montes JWT)
                .anyRequest().authenticated()
            )

            // Sin sesiones (stateless). Cuando agregues filtro JWT, esto ya está correcto
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Importante: NO usar httpBasic() si la API es pública para registro/login
        return http.build();
    }
}
