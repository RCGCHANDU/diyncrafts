package com.diyncrafts.web.app.transcoding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Called by the listener container when retries are exhausted: marks the task FAILED, removes its
 * files and rejects the message without requeueing, so RabbitMQ moves it to the dead-letter queue.
 */
@Component
public class TranscodingFailureRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(TranscodingFailureRecoverer.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TranscodingTaskService tasks;
    private final WorkDirectories workDirectories;

    public TranscodingFailureRecoverer(TranscodingTaskService tasks, WorkDirectories workDirectories) {
        this.tasks = tasks;
        this.workDirectories = workDirectories;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        String taskId = taskId(message);
        log.error("Giving up on transcoding message for task {} after retries; dead-lettering it", taskId, cause);
        if (taskId != null) {
            try {
                tasks.fail(taskId, "Processing failed repeatedly. Please upload the video again.");
                workDirectories.deleteQuietly(taskId);
            } catch (RuntimeException e) {
                log.error("Could not mark task {} as failed", taskId, e);
            }
        }
        throw new AmqpRejectAndDontRequeueException("Transcoding retries exhausted", cause);
    }

    /**
     * Reads the job directly: unlike the listener, there is no target parameter type here, and the
     * converter deliberately does not trust type headers from application packages.
     */
    private static String taskId(Message message) {
        try {
            return JSON.readValue(message.getBody(), TranscodingJob.class).taskId();
        } catch (JacksonException e) {
            return null;
        }
    }
}
