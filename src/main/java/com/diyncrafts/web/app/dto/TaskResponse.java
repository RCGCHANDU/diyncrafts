package com.diyncrafts.web.app.dto;

import java.time.LocalDateTime;

import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;

/**
 * Transcoding task status. Internal file-system paths are intentionally not exposed.
 */
public record TaskResponse(
        String taskId,
        TaskStatus status,
        double progress,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long videoId,
        String outputLocation,
        String errorDetails) {

    public static TaskResponse from(Task task) {
        return task == null ? null : new TaskResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getProgress(),
                task.getStartTime(),
                task.getEndTime(),
                task.getVideoId(),
                task.getOutputLocation(),
                task.getErrorDetails());
    }
}
