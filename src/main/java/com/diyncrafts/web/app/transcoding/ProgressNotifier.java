package com.diyncrafts.web.app.transcoding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.model.ProgressMessage;
import com.diyncrafts.web.app.model.TaskStatus;

/**
 * Pushes progress to {@code /topic/progress-{taskId}}. Best-effort: with no subscribers the message is
 * simply dropped, and broker problems never affect transcoding.
 */
@Component
public class ProgressNotifier {

    private static final Logger log = LoggerFactory.getLogger(ProgressNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ProgressNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(String taskId, double progress, TaskStatus status) {
        try {
            messagingTemplate.convertAndSend("/topic/progress-" + taskId,
                    new ProgressMessage(taskId, progress, status.name()));
        } catch (MessagingException e) {
            log.debug("Could not publish progress for task {}", taskId, e);
        }
    }
}
