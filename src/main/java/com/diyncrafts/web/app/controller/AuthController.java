package com.diyncrafts.web.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.AuthenticationResponse;
import com.diyncrafts.web.app.dto.LoginRequest;
import com.diyncrafts.web.app.dto.RegisterRequest;
import com.diyncrafts.web.app.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.ok(authService.register(registerRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        AuthenticationResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> authExceptionHandler(Exception e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid request");
        problemDetail.setDetail(e.getMessage());
        return new ResponseEntity<>(problemDetail, HttpStatus.BAD_REQUEST);
    }
}
