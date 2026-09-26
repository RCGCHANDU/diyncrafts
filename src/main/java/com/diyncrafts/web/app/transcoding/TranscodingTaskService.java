package com.diyncrafts.web.app.transcoding;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

/**
 * Task state transitions, each in its own short transaction. No transaction (and no managed entity)
 * is held while ffmpeg runs or files are uploaded.
 * <pre>
 * QUEUED ──start──▶ PROCESSING ──complete──▶ COMPLETED
 *                        │  ▲ (retry / redelivery)
 *                        └──┴──fail──▶ FAILED
 * </pre>
 * COMPLETED and FAILED are terminal: a redelivered message for such a task is ignored.
 */
@Service
public class TranscodingTaskService {

    private static final Logger log = LoggerFactory.getLogger(TranscodingTaskService.class);
    private static final int MAX_ERROR_LENGTH = 255;

    private final TaskRepository taskRepository;
    private final VideoRepository videoRepository;
    private final Clock clock;

    public TranscodingTaskService(TaskRepository taskRepository, VideoRepository videoRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.videoRepository = videoRepository;
        this.clock = clock;
    }

    public record TaskSnapshot(String taskId, Long videoId, String inputPath) {
    }

    /**
     * Moves a task to PROCESSING, or returns empty if it is unknown or already finished.
     */
    @Transactional
    public Optional<TaskSnapshot> start(String taskId, Long videoId) {
        Optional<Task> found = taskRepository.findById(taskId);
        if (found.isEmpty()) {
            log.warn("Discarding transcoding job for unknown task {}", taskId);
            return Optional.empty();
        }
        Task task = found.get();
        if (isTerminal(task.getStatus())) {
            log.info("Ignoring duplicate delivery for task {} in state {}", taskId, task.getStatus());
            return Optional.empty();
        }
        Long effectiveVideoId = task.getVideoId() != null ? task.getVideoId() : videoId;
        task.setStatus(TaskStatus.PROCESSING);
        task.setProgress(0);
        task.setStartTime(LocalDateTime.now(clock));
        task.setEndTime(null);
        task.setErrorDetails(null);
        log.info("Task {} state -> PROCESSING (video {})", taskId, effectiveVideoId);
        return Optional.of(new TaskSnapshot(taskId, effectiveVideoId, task.getInputPath()));
    }

    @Transactional
    public void updateProgress(String taskId, double progress) {
        taskRepository.updateProgress(taskId, progress, TaskStatus.PROCESSING);
    }

    @Transactional(readOnly = true)
    public boolean needsThumbnail(Long videoId) {
        return videoRepository.findById(videoId).map(video -> video.getThumbnailUrl() == null).orElse(false);
    }

    /**
     * Marks the task COMPLETED and publishes the manifest URL on the video.
     *
     * @return the updated search document, or empty if the video was deleted meanwhile (task FAILED)
     */
    @Transactional
    public Optional<VideoElasticSearch> complete(String taskId, Long videoId, String manifestUrl,
            String generatedThumbnailUrl) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        Optional<Video> video = videoRepository.findById(videoId);
        if (video.isEmpty()) {
            markFailed(task, "The video was deleted before processing finished.");
            return Optional.empty();
        }
        video.get().setVideoUrl(manifestUrl);
        if (generatedThumbnailUrl != null && video.get().getThumbnailUrl() == null) {
            video.get().setThumbnailUrl(generatedThumbnailUrl);
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setOutputLocation(manifestUrl);
        task.setEndTime(LocalDateTime.now(clock));
        log.info("Task {} state -> COMPLETED", taskId);
        return Optional.of(VideoElasticSearch.from(video.get()));
    }

    /**
     * Marks the task FAILED with a message that is safe to show to the uploader. A COMPLETED task is
     * never downgraded.
     */
    @Transactional
    public void fail(String taskId, String userMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() != TaskStatus.COMPLETED) {
                markFailed(task, userMessage);
            }
        });
    }

    /**
     * Fails tasks stuck in PROCESSING since before {@code cutoff} (e.g. the worker died and the broker
     * gave up redelivering).
     */
    @Transactional
    public List<String> failStale(LocalDateTime cutoff) {
        List<String> failed = new ArrayList<>();
        for (Task task : taskRepository.findByStatusAndStartTimeBefore(TaskStatus.PROCESSING, cutoff)) {
            markFailed(task, "Processing did not finish. Please upload the video again.");
            failed.add(task.getTaskId());
        }
        return failed;
    }

    private void markFailed(Task task, String message) {
        task.setStatus(TaskStatus.FAILED);
        task.setErrorDetails(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        task.setEndTime(LocalDateTime.now(clock));
        log.info("Task {} state -> FAILED: {}", task.getTaskId(), task.getErrorDetails());
    }

    private static boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED;
    }
}
