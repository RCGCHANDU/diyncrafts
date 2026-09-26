package com.diyncrafts.web.app.dto;

import com.diyncrafts.web.app.model.Step;

public record StepResponse(Long id, int stepNumber, String title, String description, String videoTimestamp) {

    public static StepResponse from(Step step) {
        return new StepResponse(step.getId(), step.getStepNumber(), step.getTitle(), step.getDescription(),
                step.getVideoTimestamp());
    }
}
