package org.example.dip2.security;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AuthenticatedUser(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String provider,
        String role
) {
    public boolean isGuest() {
        return "ROLE_GUEST".equals(role);
    }
}
