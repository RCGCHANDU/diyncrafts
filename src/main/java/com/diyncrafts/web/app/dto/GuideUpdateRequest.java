package com.diyncrafts.web.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuideUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 60_000) String content) {
}
