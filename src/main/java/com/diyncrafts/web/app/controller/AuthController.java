package com.diyncrafts.web.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.AuthenticationResponse;
import com.diyncrafts.web.app.dto.LoginRequest;
import com.diyncrafts.web.app.dto.RegisterRequest;
import com.diyncrafts.web.app.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Keeps the historical plain-text success body for client compatibility.
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/admin/login")
    public AuthenticationResponse adminLogin(@Valid @RequestBody LoginRequest request) {
        return authService.authenticateAdmin(request);
    }
}
