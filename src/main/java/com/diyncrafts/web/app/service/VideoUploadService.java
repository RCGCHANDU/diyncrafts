package com.diyncrafts.web.app.service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
import com.diyncrafts.web.app.repository.jpa.TaskRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.AccessGuard;

@Service
public class VideoUploadService {

    private final String uploadPath;
    private final TaskRepository taskRepository;
    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final AmqpTemplate rabbitTemplate;
    private final AccessGuard accessGuard;

    public VideoUploadService(@Value("${file.upload.path}") String uploadPath, TaskRepository taskRepository,
            VideoRepository videoRepository, VideoService videoService, AmqpTemplate rabbitTemplate,
            AccessGuard accessGuard) {
        this.uploadPath = uploadPath;
        this.taskRepository = taskRepository;
        this.videoRepository = videoRepository;
        this.videoService = videoService;
        this.rabbitTemplate = rabbitTemplate;
        this.accessGuard = accessGuard;
    }

    public VideoAndTaskResponse upload(VideoUploadRequest request, Authentication authentication) throws IOException {
        if (request.videoFile() == null || request.videoFile().isEmpty()) {
            throw new InvalidRequestException("videoFile is required and must not be empty.");
        }
        Video video = videoService.createVideo(request, authentication);
        String taskId = initiateTranscoding(request.videoFile(), video.getId());
        Task task = taskRepository.findById(taskId).orElse(null);
        return new VideoAndTaskResponse(VideoResponse.from(video), TaskResponse.from(task));
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

    private String initiateTranscoding(MultipartFile file, Long videoId) {
        String taskId = UUID.randomUUID().toString();
        String inputDir = uploadPath + taskId;
        String inputPath = inputDir + "/input.mp4";
        try {
            File dir = new File(inputDir);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Failed to create directory: " + inputDir);
            }
            file.transferTo(new File(inputPath));

            Task task = new Task();
            task.setTaskId(taskId);
            task.setStatus(TaskStatus.QUEUED);
            task.setProgress(0.0);
            task.setStartTime(LocalDateTime.now());
            task.setInputPath(inputPath);
            task.setVideoId(videoId);
            taskRepository.save(task);

            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("videoId", videoId);
            rabbitTemplate.convertAndSend("transcoding.queue", message);
            return taskId;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initiate transcoding", e);
        }
    }
}
