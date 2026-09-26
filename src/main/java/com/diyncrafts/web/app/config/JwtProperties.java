package com.diyncrafts.web.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JWT signing settings. The secret must be supplied externally (e.g. APP_JWT_SECRET);
 * there is intentionally no default so a misconfigured deployment fails at startup.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        // HS256 requires a key of at least 256 bits.
        @NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits) long") String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer) {
}
