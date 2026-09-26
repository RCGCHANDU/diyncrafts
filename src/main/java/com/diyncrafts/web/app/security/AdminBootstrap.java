package com.diyncrafts.web.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.repository.jpa.UserRepository;

/**
 * Creates the first administrator from {@code app.bootstrap-admin.*} (e.g. APP_BOOTSTRAP_ADMIN_USERNAME)
 * because self-service registration can only create regular users.
 * <p>
 * It never promotes an existing account: otherwise anyone who registered the configured username
 * first would silently become an admin.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.email:}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        if (!StringUtils.hasText(password) || password.length() < 12 || !StringUtils.hasText(email)) {
            throw new IllegalStateException(
                    "app.bootstrap-admin requires a password of at least 12 characters and an email");
        }
        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            if (existing.getRole() != User.ERole.ROLE_ADMIN) {
                log.error("Bootstrap admin '{}' already exists as a non-admin account; it was NOT promoted",
                        username);
            }
        }, () -> {
            User admin = new User();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setEmail(email);
            admin.setRole(User.ERole.ROLE_ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Created bootstrap administrator '{}'", username);
        });
    }
}
