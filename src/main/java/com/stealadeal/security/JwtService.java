package com.stealadeal.security;

import com.stealadeal.config.AuthProperties;
import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HS256 JWT access tokens. Stateless: the filter verifies signature +
 * expiry and builds the principal from claims (no DB hit). Secret is
 * config-driven ({@code app.auth.jwt-secret} ← {@code JWT_SECRET}).
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final int accessTtlMinutes;

    public JwtService(AuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = properties.accessTtlMinutes();
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    public IssuedToken issueAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTtlMinutes * 60L);
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key);
        if (user.getDealerId() != null) {
            builder.claim("dealerId", user.getDealerId());
        }
        return new IssuedToken(builder.compact(),
                OffsetDateTime.ofInstant(exp, ZoneOffset.UTC));
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Number uid = c.get("uid", Number.class);
            Number dealerId = c.get("dealerId", Number.class);
            return Optional.of(new AuthenticatedUser(
                    uid == null ? null : uid.longValue(),
                    c.getSubject(),
                    c.get("name", String.class),
                    UserRole.valueOf(c.get("role", String.class)),
                    dealerId == null ? null : dealerId.longValue()));
        } catch (RuntimeException e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
