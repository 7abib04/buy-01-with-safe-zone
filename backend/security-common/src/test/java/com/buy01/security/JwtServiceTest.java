package com.buy01.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String RAW_SECRET = "unit-test-secret-key-with-enough-length-for-hs256";

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", RAW_SECRET);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(RAW_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String token(Function<io.jsonwebtoken.JwtBuilder, io.jsonwebtoken.JwtBuilder> customizer) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", "user-1")
                .claim("role", "CLIENT")
                .expiration(Date.from(java.time.Instant.now().plusSeconds(3600)));
        return customizer.apply(builder).signWith(key()).compact();
    }

    @Test
    void parseTokenReturnsAuthenticatedUserForValidToken() {
        String jwt = token(Function.identity());

        AuthenticatedUser user = jwtService.parseToken(jwt);

        assertThat(user.userId()).isEqualTo("user-1");
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.role()).isEqualTo("CLIENT");
    }

    @Test
    void parseTokenThrowsForExpiredToken() {
        String jwt = token(b -> b.expiration(Date.from(java.time.Instant.now().minusSeconds(3600))));

        assertThatThrownBy(() -> jwtService.parseToken(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseTokenThrowsWhenRoleClaimIsMissing() {
        String jwt = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", "user-1")
                .expiration(Date.from(java.time.Instant.now().plusSeconds(3600)))
                .signWith(key())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(jwt))
                .isInstanceOf(JwtException.class)
                .hasMessage("Token is missing required claims");
    }

    @Test
    void parseTokenSupportsBase64EncodedSecret() {
        String base64Secret = Base64.getEncoder().encodeToString(RAW_SECRET.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "base64:" + base64Secret);
        String jwt = token(Function.identity());

        AuthenticatedUser user = jwtService.parseToken(jwt);

        assertThat(user.userId()).isEqualTo("user-1");
    }
}
