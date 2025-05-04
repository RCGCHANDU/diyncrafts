package com.diyncrafts.web.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.dto.AuthenticationResponse;
import com.diyncrafts.web.app.dto.LoginRequest;
import com.diyncrafts.web.app.dto.RegisterRequest;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.security.JwtTokenProvider;

@Service
public class AuthService {

    @Autowired
    private UserDetailsService userDetailsService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEnabled(true);
        user.setEmail(registerRequest.getEmail());
        user.setRole(ERole.valueOf(registerRequest.getRole()));

        userRepository.save(user);

        return "User registered sucessfully";
    }

    public AuthenticationResponse login(LoginRequest loginRequest) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails.getUsername(),
                    loginRequest.getPassword(),
                    userDetails.getAuthorities());
            authenticationManager.authenticate(authentication);
            String token = jwtTokenProvider.generateToken(authentication.getName());

            // Retrieve the user from the repository to get all details
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));

            return new AuthenticationResponse(
                    token,
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name());

        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("Invalid username or password", e);
        } catch (DisabledException e) {
            throw new IllegalArgumentException("User account is disabled", e);
        } catch (LockedException e) {
            throw new IllegalArgumentException("User account is locked", e);
        } catch (UsernameNotFoundException e) {
            throw new IllegalArgumentException("User not found", e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("An unexpected error occurred during authentication", e);
        }
    }

    public AuthenticationResponse authenticateAdmin(LoginRequest loginRequest) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
    
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails.getUsername(),
                    loginRequest.getPassword(),
                    userDetails.getAuthorities()
            );
    
            authenticationManager.authenticate(authentication);
            String username = authentication.getName();
    
            // Fetch full user details from the repository
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
    
            // Enforce admin role
            if (!user.getRole().equals(ERole.ROLE_ADMIN)) {
                throw new IllegalArgumentException("Access denied: Admin role required");
            }
    
            // Generate JWT token
            String token = jwtTokenProvider.generateToken(username);
    
            // Return authentication response
            return new AuthenticationResponse(
                    token,
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name()
            );
    
        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("Invalid username or password", e);
        } catch (DisabledException e) {
            throw new IllegalArgumentException("User account is disabled", e);
        } catch (LockedException e) {
            throw new IllegalArgumentException("User account is locked", e);
        } catch (UsernameNotFoundException e) {
            throw new IllegalArgumentException("User not found", e);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
