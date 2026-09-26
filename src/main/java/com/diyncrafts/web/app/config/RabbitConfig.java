package com.diyncrafts.web.app.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ topology for transcoding. Connection, template and listener container come from Spring
 * Boot ({@code spring.rabbitmq.*}, including listener retry); Boot applies the {@link MessageConverter}
 * and the {@code TranscodingFailureRecoverer} bean.
 * <p>
 * The job queue is a quorum queue with a delivery limit, so a message whose processing keeps crashing
 * the worker is dead-lettered by the broker instead of being redelivered forever. Rejected messages
 * (retries exhausted, unreadable payloads) go to {@value #TRANSCODING_DLQ} for inspection.
 * <p>
 * These names replace the legacy {@code transcoding.queue}; queue arguments cannot be changed on an
 * existing queue, so a new name was required.
 */
@Configuration
@EnableScheduling
public class RabbitConfig {

    public static final String TRANSCODING_QUEUE = "diyncrafts.transcoding";
    public static final String TRANSCODING_DLQ = "diyncrafts.transcoding.dlq";
    static final int DELIVERY_LIMIT = 3;

    @Bean
    public Queue transcodingQueue() {
        return QueueBuilder.durable(TRANSCODING_QUEUE)
                .quorum()
                .deliveryLimit(DELIVERY_LIMIT)
                .deadLetterExchange("")
                .deadLetterRoutingKey(TRANSCODING_DLQ)
                .build();
    }

    @Bean
    public Queue transcodingDeadLetterQueue() {
        return QueueBuilder.durable(TRANSCODING_DLQ).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
