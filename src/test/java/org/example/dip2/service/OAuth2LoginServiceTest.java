package org.example.dip2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.dip2.config.JwtProperties;
import org.example.dip2.dto.auth.AuthResponse;
import org.example.dip2.model.AuthProvider;
import org.example.dip2.model.User;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2LoginServiceTest {

    @Test
    void completeGoogleLogin_keepsExistingProfileFields() {
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded")
                .displayName("Custom Name")
                .avatarUrl("custom-avatar-seed")
                .provider(AuthProvider.GOOGLE)
                .build();

        UserRepository userRepository = inMemoryUserRepository(existingUser);
        OAuth2LoginService service = new OAuth2LoginService(userRepository, jwtService());

        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of(
                        "email", existingUser.getEmail(),
                        "name", "Google Name",
                        "picture", "https://google.example/avatar.png"
                ),
                "email"
        );
        OAuth2AuthenticationToken authenticationToken =
                new OAuth2AuthenticationToken(oauth2User, oauth2User.getAuthorities(), "google");

        AuthResponse response = service.completeGoogleLogin(authenticationToken);

        assertEquals("Custom Name", response.identity().displayName());
        assertEquals("custom-avatar-seed", response.identity().avatarUrl());
        assertEquals("GOOGLE", response.identity().provider());
    }

    private JwtService jwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setAccessTokenExpiration(Duration.ofHours(1));
        properties.setGuestTokenExpiration(Duration.ofHours(1));
        return new JwtService(properties);
    }

    private UserRepository inMemoryUserRepository(User existingUser) {
        Map<String, User> usersByEmail = new ConcurrentHashMap<>();
        usersByEmail.put(existingUser.getEmail(), existingUser);

        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findByEmail" -> Optional.ofNullable(usersByEmail.get(((String) args[0]).trim().toLowerCase()));
                        case "save" -> {
                            User user = (User) args[0];
                            usersByEmail.put(user.getEmail().trim().toLowerCase(), user);
                            yield user;
                        }
                        case "existsByEmail" -> usersByEmail.containsKey(((String) args[0]).trim().toLowerCase());
                        default -> throw new UnsupportedOperationException("Unexpected method: " + method.getName());
                    };
                }
        );
    }
}
