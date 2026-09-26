package com.diyncrafts.web.app.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * @param workDir directory for uploaded inputs and transcoding output; each task gets its own
 *                sub-directory which is deleted when the task reaches a terminal state
 */
@Validated
@ConfigurationProperties(prefix = "app.transcoding")
public record TranscodingProperties(@NotNull Path workDir) {
}
