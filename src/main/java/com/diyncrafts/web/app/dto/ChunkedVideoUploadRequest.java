package com.diyncrafts.web.app.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Metadata sent after all video chunks have reached the server. */
public record ChunkedVideoUploadRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 100) String category,
        @NotBlank @Size(max = 50) String difficultyLevel,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> materialsUsed,
        MultipartFile thumbnailFile,
        @NotBlank @Size(max = 255) String videoFileName,
        String videoContentType,
        @Min(1) @Max(512) int chunkCount,
        @Min(1) @Max(2_147_483_648L) long totalBytes) {
}

