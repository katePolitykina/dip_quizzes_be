package org.example.dip2.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotBlank @Size(min = 3, max = 50) String displayName,
        String avatarUrl
) {
}
