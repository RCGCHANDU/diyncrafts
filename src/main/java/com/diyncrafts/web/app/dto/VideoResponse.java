package com.diyncrafts.web.app.dto;

import java.time.LocalDate;
import java.util.List;

import com.diyncrafts.web.app.model.Video;

/**
 * Public representation of a video. {@code videoUrl} points at the DASH manifest once transcoding
 * has completed.
 */
public record VideoResponse(
        Long id,
        String title,
        String description,
        String thumbnailUrl,
        LocalDate uploadDate,
        Long viewCount,
        String difficultyLevel,
        String videoUrl,
        List<String> materialsUsed,
        CategoryResponse category,
        UserSummaryResponse user) {

    public static VideoResponse from(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.getThumbnailUrl(),
                video.getUploadDate(),
                video.getViewCount(),
                video.getDifficultyLevel(),
                video.getVideoUrl(),
                video.getMaterialsUsed() == null ? List.of() : List.copyOf(video.getMaterialsUsed()),
                CategoryResponse.from(video.getCategory()),
                UserSummaryResponse.from(video.getUser()));
    }
}
