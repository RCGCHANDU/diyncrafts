package com.diyncrafts.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;

import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.transcoding.MediaFixtures;
import com.diyncrafts.web.app.transcoding.StaleTaskWatchdog;
import com.diyncrafts.web.app.transcoding.TranscodingFailureRecoverer;
import com.diyncrafts.web.app.transcoding.TranscodingJob;
import com.diyncrafts.web.app.transcoding.TranscodingWorker;
import com.diyncrafts.web.app.transcoding.WorkDirectories;
import com.diyncrafts.web.support.IntegrationTestSupport;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The worker end to end with the real ffmpeg (skipped when not installed), a real database and a
 * mocked S3 client.
 */
class TranscodingPipelineIntegrationTest extends IntegrationTestSupport {

    @Autowired
    TranscodingWorker worker;
    @Autowired
    TranscodingFailureRecoverer recoverer;
    @Autowired
    StaleTaskWatchdog watchdog;
    @Autowired
    WorkDirectories workDirectories;
    @Autowired
    MessageConverter messageConverter;

    User owner;
    Video video;

    @BeforeEach
    void setUp() {
        MediaFixtures.assumeFfmpeg();
        owner = createUser("owner", ERole.ROLE_USER);
        video = createVideo(owner, "Birdhouse");
        video.setVideoUrl(null);
        video.setThumbnailUrl(null);
        video = videoRepository.save(video);
    }

    @Test
    void successfulJobCompletesTaskPublishesUrlsAndCleansUp() throws Exception {
        String taskId = queuedTask(MediaFixtures::withAudio);
        worker.onJob(new TranscodingJob(taskId, video.getId()));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getProgress()).isEqualTo(100.0);
        assertThat(task.getEndTime()).isNotNull();
        Video updated = videoRepository.findById(video.getId()).orElseThrow();
        assertThat(updated.getVideoUrl()).isEqualTo("https://cdn.test/videos/" + taskId + "/manifest.mpd");
        assertThat(updated.getThumbnailUrl()).matches("https://cdn\\.test/thumbnails/[0-9a-f-]{36}\\.jpg");

        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.atLeast(3)).putObject(puts.capture(), any(RequestBody.class));
        assertThat(puts.getAllValues()).extracting(PutObjectRequest::key)
                .contains("videos/" + taskId + "/manifest.mpd")
                .anyMatch(key -> key.startsWith("videos/" + taskId + "/chunk_"))
                .anyMatch(key -> key.startsWith("thumbnails/"));
        assertThat(workDirectories.taskDir(taskId)).doesNotExist();

        // Redelivery of the same message is a no-op (idempotent).
        clearInvocations(s3Client);
        worker.onJob(new TranscodingJob(taskId, video.getId()));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void uploadedThumbnailIsKept() throws Exception {
        video.setThumbnailUrl("https://cdn.test/thumbnails/user.png");
        videoRepository.save(video);
        String taskId = queuedTask(MediaFixtures::withoutAudio);
        worker.onJob(new TranscodingJob(taskId, video.getId()));
        assertThat(videoRepository.findById(video.getId()).orElseThrow().getThumbnailUrl())
                .isEqualTo("https://cdn.test/thumbnails/user.png");
    }

    @Test
    void corruptVideoFailsTaskWithSafeMessageAndCleansUp() throws Exception {
        String taskId = queuedTask(input -> Files.write(input, "garbage".getBytes()));
        worker.onJob(new TranscodingJob(taskId, video.getId()));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorDetails()).isEqualTo("The video could not be processed.");
        assertThat(workDirectories.taskDir(taskId)).doesNotExist();
        assertThat(videoRepository.findById(video.getId()).orElseThrow().getVideoUrl()).isNull();
    }

    @Test
    void missingInputFailsTask() throws Exception {
        String taskId = queuedTask(MediaFixtures::withAudio);
        workDirectories.deleteQuietly(taskId);
        worker.onJob(new TranscodingJob(taskId, video.getId()));
        assertThat(taskRepository.findById(taskId).orElseThrow().getErrorDetails())
                .isEqualTo("The uploaded file is no longer available. Please upload it again.");
    }

    @Test
    void storageOutagePropagatesForRetryThenRecovererFailsTaskAndDeadLetters() throws Exception {
        String taskId = queuedTask(MediaFixtures::withAudio);
        doThrow(SdkClientException.create("S3 unreachable"))
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> worker.onJob(new TranscodingJob(taskId, video.getId())))
                .hasMessageContaining("Failed to store object");
        // Retryable: state is PROCESSING and the input is kept for the next attempt.
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.PROCESSING);
        assertThat(workDirectories.input(taskId)).exists();

        Message message = messageConverter.toMessage(new TranscodingJob(taskId, video.getId()), null);
        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("boom")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorDetails()).isEqualTo("Processing failed repeatedly. Please upload the video again.");
        assertThat(workDirectories.taskDir(taskId)).doesNotExist();
    }

    @Test
    void unknownTaskIsDiscardedAndUnreadableMessagesAreRejected() throws Exception {
        worker.onJob(new TranscodingJob("no-such-task", video.getId()));
        assertThat(taskRepository.count()).isZero();
        Message garbage = new Message("{not json".getBytes());
        assertThatThrownBy(() -> recoverer.recover(garbage, new RuntimeException("bad")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void watchdogFailsTasksStuckInProcessing() throws Exception {
        String taskId = queuedTask(MediaFixtures::withAudio);
        Task task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartTime(LocalDateTime.now().minusDays(1));
        taskRepository.save(task);

        watchdog.failStaleTasks();
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(workDirectories.taskDir(taskId)).doesNotExist();
    }

    private String queuedTask(InputWriter writer) throws Exception {
        String taskId = java.util.UUID.randomUUID().toString();
        Path input = workDirectories.input(taskId);
        Files.createDirectories(input.getParent());
        writer.write(input);
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.QUEUED);
        task.setStartTime(LocalDateTime.now());
        task.setInputPath(input.toString());
        task.setVideoId(video.getId());
        taskRepository.save(task);
        return taskId;
    }

    @FunctionalInterface
    interface InputWriter {
        void write(Path input) throws Exception;
    }
}
