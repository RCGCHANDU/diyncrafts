package com.diyncrafts.web.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The same image runs as the "api" or the "worker" role; these tests pin down what each profile
 * changes. Listener containers are kept stopped (test properties override the profiles) so no broker
 * is needed.
 */
class RuntimeRoleProfilesTest {

    private static long shutdownTimeoutOf(RabbitListenerEndpointRegistry registry) {
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        return (long) ReflectionTestUtils.getField(container, "shutdownTimeout");
    }

    @Nested
    @SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
    @ActiveProfiles({"test", "worker"})
    class Worker {

        @Autowired
        Environment environment;

        @Autowired
        RabbitListenerEndpointRegistry registry;

        @Test
        void drainsTheRunningJobAndLeavesMigrationsToTheApi() {
            // Longer than the 25 min transcoding timeout, and within the lifecycle phase timeout.
            assertThat(shutdownTimeoutOf(registry)).isEqualTo(30 * 60 * 1000L);
            assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("31m");
            assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("app.search.initialize-index")).isEqualTo("false");
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles({"test", "api"})
    class Api {

        @Autowired
        Environment environment;

        @Autowired
        RabbitListenerEndpointRegistry registry;

        @Test
        void neverConsumesTranscodingJobs() {
            assertThat(environment.getProperty("spring.rabbitmq.listener.simple.auto-startup")).isEqualTo("false");
            assertThat(registry.getListenerContainers()).allSatisfy(c -> assertThat(c.isRunning()).isFalse());
            // In-flight requests (uploads) get two minutes to finish when a deploy stops this colour.
            assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("2m");
            assertThat(shutdownTimeoutOf(registry)).isEqualTo(25_000L);
        }
    }
}
