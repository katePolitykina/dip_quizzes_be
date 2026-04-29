package org.example.dip2.dto.user;

public record UserMeResponse(
        String id,
        String email,
        String displayName,
        String avatarUrl,
        String provider
) {
}
