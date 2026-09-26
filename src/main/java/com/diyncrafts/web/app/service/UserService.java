package com.diyncrafts.web.app.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.exceptions.ConflictException;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.security.AccessGuard;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves the acting user from the authenticated principal (never from client-supplied ids).
     */
    @Transactional(readOnly = true)
    public User currentUser(Authentication authentication) {
        AccessGuard.requireAuthenticated(authentication);
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional
    public User updateEmail(Authentication authentication, String email) {
        User user = currentUser(authentication);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered.");
        }
        user.setEmail(email);
        return userRepository.save(user);
    }
}
