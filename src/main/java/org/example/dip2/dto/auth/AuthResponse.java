package org.example.dip2.dto.auth;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String role,
        UserIdentity identity
) {
    public record UserIdentity(
            String id,
            String email,
            String displayName,
            String avatarUrl,
            String provider
    ) {
    }
}
