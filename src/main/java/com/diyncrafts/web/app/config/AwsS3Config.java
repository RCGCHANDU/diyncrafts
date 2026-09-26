package com.diyncrafts.web.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
public class AwsS3Config {

    /**
     * Credentials come from the standard AWS provider chain (environment variables, shared profile,
     * container/instance role); they are never read from application configuration.
     */
    @Bean
    public S3AsyncClient s3Client(@Value("${aws.s3.region}") String region) {
        return S3AsyncClient.crtBuilder()
                .region(Region.of(region))
                .build();
    }
}
