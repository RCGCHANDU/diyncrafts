package com.diyncrafts.web.app.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.VideoMetadata;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;

import org.springframework.amqp.core.AmqpTemplate;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoUploadService {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VideoDatabaseService videoDatabaseService;

    @Autowired
    private AmqpTemplate rabbitTemplate; // For sending to RabbitMQ

    public String initiateTranscoding(MultipartFile file, Long videoId) {
        String taskId = UUID.randomUUID().toString();

        // 1. Save the uploaded file to a temporary directory
        String inputDir = uploadPath + taskId;
        String inputPath = inputDir + "/input.mp4";

        try {
            // Create directory if it doesn't exist
            File dir = new File(inputDir);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Failed to create directory: " + inputDir);
            }

            // Save the uploaded file
            File inputFile = new File(inputPath);
            file.transferTo(inputFile);

            // 2. Create the Task entity
            Task task = new Task();
            task.setTaskId(taskId);
            task.setStatus(TaskStatus.QUEUED);
            task.setProgress(0.0);
            task.setStartTime(LocalDateTime.now());
            task.setInputPath(inputPath);
            task.setOutputLocation(null);
            task.setErrorDetails(null);

            // 3. Save the task to the database
            taskRepository.save(task);

            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("videoId", videoId);

            // 4. Send the task ID to the RabbitMQ queue
            rabbitTemplate.convertAndSend("transcoding.queue", message);

            return taskId;
        } catch (IOException | RuntimeException e) {
            // Handle errors (e.g., cleanup, logging)
            throw new RuntimeException("Failed to initiate transcoding: " + e.getMessage(), e);
        }
    }

    public Video createVideo(VideoMetadata videoMetadata, Authentication authentication) throws IOException {
        return videoDatabaseService.createVideo(videoMetadata, authentication);
    }

    public Task getTask(String taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }
}