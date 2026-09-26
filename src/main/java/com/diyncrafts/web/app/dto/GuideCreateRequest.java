package com.diyncrafts.web.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GuideCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 60_000) String content,
        @NotNull @Positive Long videoId) {
}
