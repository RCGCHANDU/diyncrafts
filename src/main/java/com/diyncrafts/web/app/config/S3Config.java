package com.diyncrafts.web.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * The single S3 client of the application. Credentials come from the standard AWS provider chain
 * (environment, shared profile, container/instance role) and are never part of application config.
 */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.pathStyleAccess());
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }
}
