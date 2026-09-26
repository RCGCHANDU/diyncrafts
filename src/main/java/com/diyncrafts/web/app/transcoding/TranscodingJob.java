package com.diyncrafts.web.app.transcoding;

/**
 * RabbitMQ message asking a worker to transcode the uploaded input of {@code taskId}.
 */
public record TranscodingJob(String taskId, Long videoId) {
}
