package com.diyncrafts.web.app.model;

/**
 * STOMP payload on {@code /topic/progress-{taskId}}.
 *
 * @param progress percentage 0-100
 * @param status   PROCESSING, COMPLETED or FAILED (added field; existing clients can ignore it)
 */
public record ProgressMessage(String taskId, double progress, String status) {
}
