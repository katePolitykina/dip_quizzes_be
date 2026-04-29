package org.example.dip2.dto.user;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 50) String displayName,
        String avatarUrl
) {
}
