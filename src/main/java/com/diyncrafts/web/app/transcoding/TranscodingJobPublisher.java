package com.diyncrafts.web.app.transcoding;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.config.RabbitConfig;

@Component
public class TranscodingJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TranscodingJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String taskId, Long videoId) {
        rabbitTemplate.convertAndSend(RabbitConfig.TRANSCODING_QUEUE, new TranscodingJob(taskId, videoId));
    }
}
