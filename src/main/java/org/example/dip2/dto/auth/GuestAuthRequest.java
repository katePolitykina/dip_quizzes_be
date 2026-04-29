package org.example.dip2.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestAuthRequest(
        @NotBlank @Size(min = 3, max = 50) String nickname,
        String avatarUrl
) {
}
