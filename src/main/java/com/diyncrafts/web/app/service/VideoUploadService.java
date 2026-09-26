package com.diyncrafts.web.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.config.TranscodingProperties;
import com.diyncrafts.web.app.dto.TaskResponse;
import com.diyncrafts.web.app.dto.VideoAndTaskResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.AccessGuard;
import com.diyncrafts.web.app.storage.ObjectKeys;
import com.diyncrafts.web.app.storage.ObjectStorageService;
import com.diyncrafts.web.app.transcoding.TranscodingJobPublisher;
import com.diyncrafts.web.app.transcoding.WorkDirectories;

/**
 * Accepts video uploads and hands them to the asynchronous transcoding pipeline.
 * <p>
 * Order: validate → store the optional thumbnail → save the input file → create the video and its
 * task in one short transaction → publish the job. Nothing slow runs inside the transaction, and
 * the task's work directory is removed again if a later step fails.
 */
@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);

    private final TaskRepository taskRepository;
    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final UserService userService;
    private final SearchIndexService searchIndex;
    private final ObjectStorageService storage;
    private final TranscodingJobPublisher publisher;
    private final WorkDirectories workDirectories;
    private final AccessGuard accessGuard;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public VideoUploadService(TaskRepository taskRepository, VideoRepository videoRepository,
            VideoService videoService, UserService userService, SearchIndexService searchIndex,
            ObjectStorageService storage, TranscodingJobPublisher publisher, TranscodingProperties properties,
            AccessGuard accessGuard, PlatformTransactionManager transactionManager, Clock clock) {
        this.taskRepository = taskRepository;
        this.videoRepository = videoRepository;
        this.videoService = videoService;
        this.userService = userService;
        this.searchIndex = searchIndex;
        this.storage = storage;
        this.publisher = publisher;
        this.workDirectories = new WorkDirectories(properties.workDir());
        this.accessGuard = accessGuard;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public VideoAndTaskResponse upload(VideoUploadRequest request, Authentication authentication) throws IOException {
        MultipartFile videoFile = request.videoFile();
        if (videoFile == null || videoFile.isEmpty()) {
            throw new InvalidRequestException("videoFile is required and must not be empty.");
        }
        User owner = userService.currentUser(authentication);
        // Resolve the category before any file is stored so invalid requests leave nothing behind.
        transactions.executeWithoutResult(status -> videoService.resolveCategory(request.category()));
        MultipartFile thumbnail = request.thumbnailFile();
        String thumbnailUrl = (thumbnail != null && !thumbnail.isEmpty())
                ? storage.storeImage(thumbnail, ObjectKeys::thumbnail)
                : null;

        String taskId = UUID.randomUUID().toString();
        Path input = workDirectories.input(taskId);
        Created created;
        try {
            Files.createDirectories(input.getParent());
            videoFile.transferTo(input);
            created = transactions.execute(status -> createVideoAndTask(request, owner, thumbnailUrl, taskId, input));
        } catch (IOException | RuntimeException e) {
            workDirectories.deleteQuietly(taskId);
            throw e;
        }
        log.info("Upload received: video {} task {} by '{}' ({} bytes)", created.video().id(), taskId,
                owner.getUsername(), videoFile.getSize());
        searchIndex.save(created.document());
        queue(taskId, created.video().id());
        return new VideoAndTaskResponse(created.video(), transactions.execute(status ->
                TaskResponse.from(taskRepository.findById(taskId).orElseThrow())));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskId, Authentication authentication) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        // Tasks created before video ids were recorded have no owner and are visible to admins only.
        User owner = task.getVideoId() == null ? null
                : videoRepository.findById(task.getVideoId()).map(Video::getUser).orElse(null);
        accessGuard.requireOwnerOrAdmin(owner, authentication, "task");
        return TaskResponse.from(task);
    }

    private Created createVideoAndTask(VideoUploadRequest request, User owner, String thumbnailUrl, String taskId,
            Path input) {
        Video video = new Video();
        video.setTitle(request.title());
        video.setDescription(request.description());
        video.setDifficultyLevel(request.difficultyLevel());
        video.setMaterialsUsed(VideoService.normalize(request.materialsUsed()));
        video.setCategory(videoService.resolveCategory(request.category()));
        video.setThumbnailUrl(thumbnailUrl);
        video.setUploadDate(LocalDate.now(clock));
        video.setViewCount(0L);
        video.setUser(owner);
        video = videoRepository.save(video);

        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.QUEUED);
        task.setProgress(0.0);
        task.setStartTime(LocalDateTime.now(clock));
        task.setInputPath(input.toString());
        task.setVideoId(video.getId());
        taskRepository.save(task);
        return new Created(VideoResponse.from(video), VideoElasticSearch.from(video));
    }

    private void queue(String taskId, Long videoId) {
        try {
            publisher.publish(taskId, videoId);
        } catch (AmqpException e) {
            log.error("Could not queue transcoding task {}", taskId, e);
            transactions.executeWithoutResult(status -> taskRepository.findById(taskId).ifPresent(task -> {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorDetails("Could not queue the video for processing. Please upload it again.");
                task.setEndTime(LocalDateTime.now(clock));
            }));
            workDirectories.deleteQuietly(taskId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Video processing is temporarily unavailable.", e);
        }
    }

    private record Created(VideoResponse video, VideoElasticSearch document) {
    }
}
