package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * The publisher and the {@code @RabbitListener} agree on the wire format.
 */
class TranscodingJobConversionTest {

    @Test
    void jobRoundTripsWithTheListenersInferredType() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        Message message = converter.toMessage(new TranscodingJob("task-1", 42L), new MessageProperties());
        assertThat(new String(message.getBody())).contains("\"taskId\":\"task-1\"").contains("\"videoId\":42");

        // What the listener adapter does for a single TranscodingJob parameter.
        message.getMessageProperties().setInferredArgumentType(TranscodingJob.class);
        assertThat(converter.fromMessage(message)).isEqualTo(new TranscodingJob("task-1", 42L));
    }
}
