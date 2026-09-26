package com.diyncrafts.web.app.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Multipart form for uploading a new video.
 */
public record VideoUploadRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 100) String category,
        @NotBlank @Size(max = 50) String difficultyLevel,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> materialsUsed,
        MultipartFile thumbnailFile,
        @NotNull(message = "is required") MultipartFile videoFile) {
}
