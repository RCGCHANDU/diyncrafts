package com.diyncrafts.web.app.dto;

/**
 * Returned by the login endpoints. The email belongs to the authenticating user themselves.
 */
public record AuthenticationResponse(String token, String username, String email, String role) {

    @Override
    public String toString() {
        return "AuthenticationResponse[username=" + username + ", role=" + role + "]";
    }
}
