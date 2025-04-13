package com.diyncrafts.web.app.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.VideoMetadata;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.service.VideoUploadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/videos")
public class VideoUploadController {
    @Autowired
    private VideoUploadService taskService;

    @Autowired
    private VideoUploadService videoUploadService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Task> upload(
        @RequestParam("videoFile") MultipartFile file,
        @Valid @ModelAttribute VideoMetadata videoUploadRequest
        ) throws IOException {
        videoUploadRequest.setVideoFile(file);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long videoId = videoUploadService.createVideo(videoUploadRequest, authentication);
        String taskId = taskService.initiateTranscoding(file, videoId);

        return ResponseEntity.ok(taskService.getTask(taskId));
    }

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public Task getTaskStatus(@PathVariable String taskId) {
        return taskService.getTask(taskId);
    }
}