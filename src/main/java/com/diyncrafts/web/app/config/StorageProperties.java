package com.diyncrafts.web.app.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Object storage (S3 or an S3-compatible service such as MinIO).
 *
 * @param bucket          bucket for thumbnails, guide images and transcoded videos
 * @param region          AWS region of the bucket
 * @param endpoint        optional endpoint override for S3-compatible services (null for AWS)
 * @param pathStyleAccess use path-style URLs (needed by most S3-compatible services)
 * @param publicBaseUrl   optional base URL used to build public object URLs, e.g. a CDN; defaults to
 *                        the bucket's virtual-hosted S3 URL
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        URI endpoint,
        boolean pathStyleAccess,
        String publicBaseUrl) {

    public String resolvedPublicBaseUrl() {
        String base = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com"
                : publicBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
