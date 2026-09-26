package com.diyncrafts.web.app.dto;

import java.util.UUID;

import com.diyncrafts.web.app.model.User;

/**
 * The authenticated user's own profile; the only user representation that includes the email.
 */
public record UserProfileResponse(UUID id, String username, String email, String role) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
