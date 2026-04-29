package org.example.dip2.service;

import org.example.dip2.dto.auth.AuthResponse;
import org.example.dip2.dto.auth.GuestAuthRequest;
import org.example.dip2.dto.auth.LoginRequest;
import org.example.dip2.dto.auth.RegisterRequest;
import org.example.dip2.exception.ApiException;
import org.example.dip2.model.AuthProvider;
import org.example.dip2.model.User;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .avatarUrl(normalizeOptional(request.avatarUrl()))
                .provider(AuthProvider.LOCAL)
                .build();

        User savedUser = userRepository.save(user);
        JwtService.TokenPayload tokenPayload = jwtService.issueUserToken(savedUser);
        return new AuthResponse(tokenPayload.token(), "Bearer", tokenPayload.expiresAt(), "ROLE_USER", tokenPayload.identity());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        JwtService.TokenPayload tokenPayload = jwtService.issueUserToken(user);
        return new AuthResponse(tokenPayload.token(), "Bearer", tokenPayload.expiresAt(), "ROLE_USER", tokenPayload.identity());
    }

    public AuthResponse issueGuestToken(GuestAuthRequest request) {
        JwtService.TokenPayload tokenPayload = jwtService.issueGuestToken(
                request.nickname().trim(),
                normalizeOptional(request.avatarUrl())
        );
        return new AuthResponse(tokenPayload.token(), "Bearer", tokenPayload.expiresAt(), "ROLE_GUEST", tokenPayload.identity());
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
