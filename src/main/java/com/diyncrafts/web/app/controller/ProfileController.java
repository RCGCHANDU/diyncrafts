package com.diyncrafts.web.app.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.UserProfileResponse;
import com.diyncrafts.web.app.service.UserService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(Authentication authentication) {
        return UserProfileResponse.from(userService.currentUser(authentication));
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(Authentication authentication,
            @RequestParam @NotBlank @Email @Size(max = 254) String mailId) {
        return UserProfileResponse.from(userService.updateEmail(authentication, mailId));
    }
}
