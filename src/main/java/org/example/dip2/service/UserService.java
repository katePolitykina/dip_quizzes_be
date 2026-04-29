package org.example.dip2.service;

import java.util.UUID;
import org.example.dip2.dto.user.UpdateUserRequest;
import org.example.dip2.dto.user.UserMeResponse;
import org.example.dip2.exception.ApiException;
import org.example.dip2.model.User;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(AuthenticatedUser authenticatedUser) {
        User user = loadUser(authenticatedUser.id());
        return toResponse(user);
    }

    @Transactional
    public UserMeResponse updateCurrentUser(AuthenticatedUser authenticatedUser, UpdateUserRequest request) {
        User user = loadUser(authenticatedUser.id());
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        return toResponse(userRepository.save(user));
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserMeResponse toResponse(User user) {
        return new UserMeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getProvider().name()
        );
    }
}
