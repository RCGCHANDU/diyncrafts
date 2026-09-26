package com.diyncrafts.web.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service registration. There is deliberately no role field: every registered account gets
 * {@code ROLE_USER}. Unknown JSON properties (such as a legacy {@code role}) are ignored.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        // BCrypt only uses the first 72 bytes; longer passwords are rejected rather than truncated.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Email @Size(max = 254) String email) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + "]";
    }
}
