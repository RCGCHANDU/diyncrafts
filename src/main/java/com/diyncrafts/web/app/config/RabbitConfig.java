package com.diyncrafts.web.app.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connection, template and listener container come from Spring Boot auto-configuration
 * ({@code spring.rabbitmq.*}); Boot applies the {@link MessageConverter} bean to both.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Queue transcodingQueue() {
        return new Queue("transcoding.queue", true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
