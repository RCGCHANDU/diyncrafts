package com.diyncrafts.webapp.service;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.diyncrafts.webapp.model.User;
import com.diyncrafts.webapp.repository.UserRepository;

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

    public User updateUserProfile(User updatedUser) {
        User currentUser = getCurrentUserProfile();
        currentUser.setEmail(updatedUser.getEmail());
        return userRepository.save(currentUser);
    }
}