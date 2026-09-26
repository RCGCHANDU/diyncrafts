package com.diyncrafts.web.app.dto;

import java.util.UUID;

import com.diyncrafts.web.app.model.User;

/**
 * Public view of a user embedded in other resources (video uploader, guide author).
 */
public record UserSummaryResponse(UUID id, String username) {

    public static UserSummaryResponse from(User user) {
        return user == null ? null : new UserSummaryResponse(user.getId(), user.getUsername());
    }
}
