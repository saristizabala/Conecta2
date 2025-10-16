package com.example.worker_registry.securtity;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT Service
 * - Mantiene los claims actuales: { "type": "activation" | "access", "role": "CLIENT"|"WORKER" }
 * - Propiedades:
 *      app.jwt.secret                (mínimo efectivo 32 bytes; si no, se rellena)
 *      app.jwt.activation.exp-min    (por defecto 15)
 *      app.jwt.access.exp-min        (por defecto 60)
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret; // puede venir corto; se rellena

    @Value("${app.jwt.activation.exp-min:15}")
    private long activationExpMin;

    @Value("${app.jwt.access.exp-min:60}")
    private long accessExpMin;

    // ======== Key ========
    private SecretKey key() {
        // Evita WeakKeyException si el secret es < 32 bytes
        String s = (secret == null) ? "" : secret.trim();
        if (s.length() < 32) {
            StringBuilder sb = new StringBuilder(s);
            while (sb.length() < 32) sb.append("_padX");
            s = sb.toString();
        }
        return Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    // =========================================================
    //                 ACTIVATION TOKEN
    // =========================================================
    public String generateActivationToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(activationExpMin * 60)))
                .addClaims(Map.of("type", "activation"))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** true si el JWT es válido y su claim "type" = "activation" */
    public boolean isActivationToken(String token) {
        try {
            Claims c = parse(token);
            return "activation".equals(String.valueOf(c.get("type")));
        } catch (Exception e) {
            return false;
        }
    }

    /** Retorna userId si y solo si es un token de activación válido; si no, lanza IllegalArgumentException */
    public Long parseActivationToken(String token) {
        Claims c = parse(token);
        Object type = c.get("type");
        if (type == null || !"activation".equals(type.toString())) {
            throw new IllegalArgumentException("Token inválido para activación");
        }
        return Long.valueOf(c.getSubject());
    }

    // =========================================================
    //                   ACCESS TOKEN
    // =========================================================
    public String generateAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessExpMin * 60)))
                .addClaims(Map.of(
                        "type", "access",
                        "role", role == null ? "ANON" : role
                ))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** true si el JWT (access o activation) es válido (firma/exp) */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Obtiene el id del usuario desde el subject (para cualquier tipo de token válido) */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /** Retorna el rol (solo para access tokens). Si no existe, "ANON". */
    public String getRole(String token) {
        Claims c = parse(token);
        Object type = c.get("type");
        if (type == null || !"access".equals(type.toString())) return "ANON";
        Object r = c.get("role");
        return r == null ? "ANON" : r.toString();
    }

    /** Verifica que el token sea de acceso; retorna Claims o lanza IllegalArgumentException */
    public Claims parseAccessToken(String token) {
        Claims c = parse(token);
        Object type = c.get("type");
        if (type == null || !"access".equals(type.toString())) {
            throw new IllegalArgumentException("Token inválido: se esperaba access token");
        }
        return c;
    }

    // =========================================================
    //                    Helper común
    // =========================================================
    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
