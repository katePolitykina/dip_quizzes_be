package org.example.dip2.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dip2.dto.user.UpdateUserRequest;
import org.example.dip2.dto.user.UserMeResponse;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserMeResponse getMe(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return userService.getCurrentUser(authenticatedUser);
    }

    @PatchMapping("/me")
    public UserMeResponse updateMe(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateCurrentUser(authenticatedUser, request);
    }
}
