package com.buy01.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    public AuthenticatedUser parseToken(String token) {
        Claims claims = extractAllClaims(token);
        Instant expiration = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
        if (expiration != null && expiration.isBefore(Instant.now())) {
            throw new io.jsonwebtoken.JwtException("Token expired");
        }

        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        String email = claims.getSubject();

        if (userId == null || role == null || email == null) {
            throw new io.jsonwebtoken.JwtException("Token is missing required claims");
        }

        return new AuthenticatedUser(userId, email, role);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(resolveSecretBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private byte[] resolveSecretBytes() {
        String secret = Objects.requireNonNull(jwtSecret, "security.jwt.secret must be configured");
        if (secret.startsWith("base64:")) {
            return io.jsonwebtoken.io.Decoders.BASE64.decode(secret.substring("base64:".length()));
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
