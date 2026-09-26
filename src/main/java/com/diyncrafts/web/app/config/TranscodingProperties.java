package com.diyncrafts.web.app.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.diyncrafts.web.app.transcoding.Rendition;
import com.diyncrafts.web.app.transcoding.VideoEncoder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Transcoding settings ({@code app.transcoding.*}).
 *
 * @param workDir        scratch space; each task gets {@code {workDir}/{taskId}}, deleted when the
 *                       task reaches a terminal state
 * @param encoder        {@code libx264} (CPU, default, works everywhere) or {@code h264_nvenc}
 *                       (NVIDIA GPU, must be supported by the installed ffmpeg)
 * @param preset         encoder preset; defaults to {@code veryfast} (libx264) or {@code p5} (NVENC)
 * @param segmentSeconds DASH segment length; key frames are forced at this interval
 * @param timeout        ffmpeg is killed after this long
 * @param renditions     output ladder by short side (height for landscape, width for portrait);
 *                       renditions larger than the source are skipped (no upscaling)
 */
@Validated
@ConfigurationProperties(prefix = "app.transcoding")
public record TranscodingProperties(
        @NotNull Path workDir,
        @NotBlank String ffmpegPath,
        @NotBlank String ffprobePath,
        @NotNull VideoEncoder encoder,
        String preset,
        @Min(1) int segmentSeconds,
        @NotNull Duration timeout,
        @NotEmpty List<@Valid Rendition> renditions) {

    public String effectivePreset() {
        return preset == null || preset.isBlank() ? encoder.defaultPreset() : preset;
    }
}
