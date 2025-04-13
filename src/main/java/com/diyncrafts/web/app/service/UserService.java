package com.diyncrafts.web.app.service;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.repository.jpa.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUserProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updateUserProfile(String mailId) {
        User currentUser = getCurrentUserProfile();
        currentUser.setEmail(mailId);
        return userRepository.save(currentUser);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}