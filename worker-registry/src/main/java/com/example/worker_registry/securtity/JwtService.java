package com.example.worker_registry.securtity;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret; // Mínimo 32 chars

    @Value("${app.jwt.activation.exp-min:15}")
    private long activationExpMin;

    @Value("${app.jwt.access.exp-min:60}")
    private long accessExpMin;

    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===== ACTIVACIÓN =====
    public String generateActivationToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "activation")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(activationExpMin * 60)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isActivationToken(String token) {
        try {
            Claims c = parse(token);
            return "activation".equals(c.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    // ===== ACCESO (JWT para usar después del login) =====
    public String generateAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "access")
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessExpMin * 60)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ===== Helper =====
    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
