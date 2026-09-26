package com.diyncrafts.web.app.dto;

import java.util.List;

import com.diyncrafts.web.app.model.Guide;

public record GuideResponse(
        Long id,
        String title,
        String content,
        String imageUrl,
        VideoSummaryResponse video,
        UserSummaryResponse user,
        List<StepResponse> steps) {

    public static GuideResponse from(Guide guide) {
        List<StepResponse> steps = guide.getSteps() == null ? List.of()
                : guide.getSteps().stream().map(StepResponse::from).toList();
        return new GuideResponse(
                guide.getId(),
                guide.getTitle(),
                guide.getContent(),
                guide.getImageUrl(),
                VideoSummaryResponse.from(guide.getVideo()),
                UserSummaryResponse.from(guide.getUser()),
                steps);
    }
}
