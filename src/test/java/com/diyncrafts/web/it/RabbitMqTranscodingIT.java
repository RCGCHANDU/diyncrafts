package com.diyncrafts.web.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.diyncrafts.web.app.config.RabbitConfig;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.transcoding.MediaFixtures;
import com.diyncrafts.web.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The transcoding pipeline through a real RabbitMQ 4.3 broker: queue topology, delivery, retries and
 * dead-lettering. Uses the real ffmpeg (skipped without it).
 */
@Testcontainers
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"})
class RabbitMqTranscodingIT extends IntegrationTestSupport {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.3-management");

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void uploadedVideoIsTranscodedThroughTheBroker() throws Exception {
        MediaFixtures.assumeFfmpeg();
        String taskId = upload();
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() ->
                assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED));
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(videoRepository.findById(task.getVideoId()).orElseThrow().getVideoUrl())
                .isEqualTo("https://cdn.test/videos/" + taskId + "/manifest.mpd");
    }

    @Test
    void persistentStorageFailureIsRetriedThenTaskFailsAndMessageIsDeadLettered() throws Exception {
        MediaFixtures.assumeFfmpeg();
        drainDeadLetters();
        String taskId = upload(); // thumbnail absent, so S3 is first used by the worker
        doThrow(SdkClientException.create("S3 unreachable"))
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        await().atMost(Duration.ofSeconds(120)).untilAsserted(() ->
                assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.FAILED));
        assertThat(taskRepository.findById(taskId).orElseThrow().getErrorDetails())
                .isEqualTo("Processing failed repeatedly. Please upload the video again.");
        Message dead = rabbitTemplate.receive(RabbitConfig.TRANSCODING_DLQ, 10_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody())).contains(taskId);
    }

    @Test
    void unreadableMessagesAreDeadLetteredNotRedeliveredForever() {
        drainDeadLetters();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitConfig.TRANSCODING_QUEUE, new Message("{not json".getBytes(), properties));

        Message dead = rabbitTemplate.receive(RabbitConfig.TRANSCODING_DLQ, 30_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody())).isEqualTo("{not json");
    }

    @Test
    void jobQueueIsAQuorumQueueWithDeliveryLimitAndDeadLetterRouting() throws Exception {
        var result = RABBIT.execInContainer("rabbitmqctl", "list_queues", "--quiet", "name", "type", "arguments");
        String line = result.getStdout().lines().filter(l -> l.startsWith(RabbitConfig.TRANSCODING_QUEUE + "\t"))
                .findFirst().orElseThrow();
        assertThat(line).contains("quorum").contains("x-delivery-limit").contains(RabbitConfig.TRANSCODING_DLQ);
    }

    private String upload() throws Exception {
        User user = userRepository.findByUsername("alice").orElseGet(() -> createUser("alice", ERole.ROLE_USER));
        Path clip = Files.createTempFile("clip", ".mp4");
        MediaFixtures.withAudio(clip);
        String body = mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", Files.readAllBytes(clip)))
                        .param("title", "Birdhouse").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Files.deleteIfExists(clip);
        return JsonPath.read(body, "$.task.taskId");
    }

    private void drainDeadLetters() {
        while (rabbitTemplate.receive(RabbitConfig.TRANSCODING_DLQ) != null) {
            // discard messages left by other tests
        }
    }
}
