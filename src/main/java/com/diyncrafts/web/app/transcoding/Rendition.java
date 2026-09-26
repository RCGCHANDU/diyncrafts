package com.diyncrafts.web.app.transcoding;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * One rung of the bitrate ladder.
 *
 * @param shortSide target size of the shorter edge in pixels (e.g. 720 for 1280x720 or 720x1280)
 */
public record Rendition(@Min(2) int shortSide, @NotBlank String bitrate, @NotBlank String maxRate,
        @NotBlank String bufferSize) {
}
