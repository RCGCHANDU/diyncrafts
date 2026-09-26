package com.diyncrafts.web.app.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.dto.AuthenticationResponse;
import com.diyncrafts.web.app.dto.LoginRequest;
import com.diyncrafts.web.app.dto.RegisterRequest;
import com.diyncrafts.web.app.exceptions.ConflictException;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.security.AccessGuard;
import com.diyncrafts.web.app.security.JwtService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Registers a regular user. The role is always {@link ERole#ROLE_USER}; administrators are
     * created out of band (see {@code AdminBootstrap}).
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidRequestException("Password must not exceed 72 bytes.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already registered.");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setEnabled(true);
        user.setRole(ERole.ROLE_USER);
        User saved = userRepository.save(user);
        log.info("Registered user '{}'", saved.getUsername());
        return saved;
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        Authentication authentication = authenticate(request);
        return toResponse(authentication);
    }

    /**
     * Same as {@link #login} but only succeeds for administrators. The issued token is identical in
     * format; admin rights come from the {@code roles} claim.
     */
    @Transactional(readOnly = true)
    public AuthenticationResponse authenticateAdmin(LoginRequest request) {
        Authentication authentication = authenticate(request);
        if (!AccessGuard.isAdmin(authentication)) {
            log.warn("Non-admin user '{}' attempted admin login", authentication.getName());
            // Explicit 403: the caller is anonymous at this point, so AccessDeniedException would map to 401.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required.");
        }
        return toResponse(authentication);
    }

    private Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
    }

    private AuthenticationResponse toResponse(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user disappeared"));
        String token = jwtService.issueToken(user.getUsername(), authentication.getAuthorities());
        return new AuthenticationResponse(token, user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
