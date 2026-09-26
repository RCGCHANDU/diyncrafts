package com.diyncrafts.web.app.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.model.User;

/**
 * Central "owner or admin" rule for user-owned resources (videos, guides, transcoding tasks).
 */
@Component
public class AccessGuard {

    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public void requireOwnerOrAdmin(User owner, Authentication authentication, String resourceName) {
        requireAuthenticated(authentication);
        if (isAdmin(authentication)) {
            return;
        }
        if (owner != null && owner.getUsername().equals(authentication.getName())) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to modify this " + resourceName + ".");
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    public static void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
    }
}
