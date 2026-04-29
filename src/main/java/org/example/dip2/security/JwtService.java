package org.example.dip2.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.example.dip2.config.JwtProperties;
import org.example.dip2.dto.auth.AuthResponse;
import org.example.dip2.model.User;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = createSigningKey(jwtProperties.getSecret());
    }

    public TokenPayload issueUserToken(User user) {
        Instant expiresAt = Instant.now().plus(jwtProperties.getAccessTokenExpiration());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("role", "ROLE_USER");
        claims.put("email", user.getEmail());
        claims.put("displayName", user.getDisplayName());
        claims.put("avatarUrl", user.getAvatarUrl());
        claims.put("provider", user.getProvider().name());

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new TokenPayload(token, expiresAt, buildIdentity(user));
    }

    public TokenPayload issueGuestToken(String nickname, String avatarUrl) {
        UUID guestId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(jwtProperties.getGuestTokenExpiration());
        String token = Jwts.builder()
                .subject(guestId.toString())
                .claims(Map.of(
                        "role", "ROLE_GUEST",
                        "displayName", nickname.trim(),
                        "avatarUrl", avatarUrl == null ? "" : avatarUrl.trim(),
                        "provider", "GUEST"
                ))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new TokenPayload(
                token,
                expiresAt,
                new AuthResponse.UserIdentity(guestId.toString(), null, nickname.trim(), avatarUrl, "GUEST")
        );
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return AuthenticatedUser.builder()
                .id(UUID.fromString(claims.getSubject()))
                .email(claims.get("email", String.class))
                .displayName(claims.get("displayName", String.class))
                .avatarUrl(emptyToNull(claims.get("avatarUrl", String.class)))
                .provider(claims.get("provider", String.class))
                .role(claims.get("role", String.class))
                .build();
    }

    private AuthResponse.UserIdentity buildIdentity(User user) {
        return new AuthResponse.UserIdentity(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getProvider().name()
        );
    }

    private SecretKey createSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record TokenPayload(String token, Instant expiresAt, AuthResponse.UserIdentity identity) {
    }
}
