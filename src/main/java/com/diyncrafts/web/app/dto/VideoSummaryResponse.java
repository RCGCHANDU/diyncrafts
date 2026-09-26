package com.diyncrafts.web.app.dto;

import com.diyncrafts.web.app.model.Video;

/**
 * Minimal video reference embedded in guides.
 */
public record VideoSummaryResponse(Long id, String title, String thumbnailUrl) {

    public static VideoSummaryResponse from(Video video) {
        return video == null ? null : new VideoSummaryResponse(video.getId(), video.getTitle(), video.getThumbnailUrl());
    }
}
