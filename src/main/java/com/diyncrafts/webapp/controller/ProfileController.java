package com.diyncrafts.webapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.webapp.service.UserService;

@RestController
@RequestMapping("/api/user")
public class ProfileController {
    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getProfile() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> updateProfile(@RequestParam String mailId) {
        return ResponseEntity.ok(userService.updateUserProfile(mailId));
    }
}
