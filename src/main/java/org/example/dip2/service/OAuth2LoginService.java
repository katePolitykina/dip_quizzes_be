package org.example.dip2.service;

import java.util.Map;
import java.util.UUID;
import org.example.dip2.dto.auth.AuthResponse;
import org.example.dip2.model.AuthProvider;
import org.example.dip2.model.User;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.security.JwtService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuth2LoginService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public OAuth2LoginService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse completeGoogleLogin(OAuth2AuthenticationToken authenticationToken) {
        OAuth2User oauthUser = authenticationToken.getPrincipal();
        Map<String, Object> attributes = oauthUser.getAttributes();

        String email = normalizedString(attributes.get("email"));
        String displayName = firstNonBlank(
                normalizedString(attributes.get("name")),
                normalizedString(attributes.get("given_name")),
                email == null ? "Google User" : email
        );
        String avatarUrl = normalizedString(attributes.get("picture"));

        User user = email == null
                ? createGoogleUser("google-" + UUID.randomUUID() + "@local.invalid", displayName, avatarUrl)
                : userRepository.findByEmail(email)
                        .map(existingUser -> updateGoogleProfile(existingUser, displayName, avatarUrl))
                        .orElseGet(() -> createGoogleUser(email, displayName, avatarUrl));

        JwtService.TokenPayload tokenPayload = jwtService.issueUserToken(user);
        return new AuthResponse(
                tokenPayload.token(),
                "Bearer",
                tokenPayload.expiresAt(),
                "ROLE_USER",
                tokenPayload.identity()
        );
    }

    private User createGoogleUser(String email, String displayName, String avatarUrl) {
        User user = User.builder()
                .email(email)
                .password(UUID.randomUUID().toString())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .provider(AuthProvider.GOOGLE)
                .build();
        return userRepository.save(user);
    }

    private User updateGoogleProfile(User user, String displayName, String avatarUrl) {
        if (isBlank(user.getDisplayName()) && !isBlank(displayName)) {
            user.setDisplayName(displayName);
        }
        if (isBlank(user.getAvatarUrl()) && !isBlank(avatarUrl)) {
            user.setAvatarUrl(avatarUrl);
        }
        user.setProvider(AuthProvider.GOOGLE);
        return userRepository.save(user);
    }

    private String normalizedString(Object value) {
        if (!(value instanceof String stringValue)) {
            return null;
        }
        String trimmed = stringValue.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Google User";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
