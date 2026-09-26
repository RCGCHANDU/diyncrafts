package com.diyncrafts.web.app.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.TaskResponse;
import com.diyncrafts.web.app.dto.VideoAndTaskResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.service.VideoUploadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/videos")
public class VideoUploadController {

    private final VideoUploadService videoUploadService;

    public VideoUploadController(VideoUploadService videoUploadService) {
        this.videoUploadService = videoUploadService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoAndTaskResponse upload(@Valid @ModelAttribute VideoUploadRequest request,
            Authentication authentication) throws IOException {
        return videoUploadService.upload(request, authentication);
    }

    /**
     * Legacy endpoint kept for existing clients. It used to store a video without transcoding it
     * (leaving an unusable URL); it now runs the same upload pipeline as {@code /upload}.
     */
    @PostMapping(path = "/create/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoResponse create(@Valid @ModelAttribute VideoUploadRequest request, Authentication authentication)
            throws IOException {
        return videoUploadService.upload(request, authentication).video();
    }

    @GetMapping("/status/{taskId}")
    public TaskResponse getTaskStatus(@PathVariable String taskId, Authentication authentication) {
        return videoUploadService.getTask(taskId, authentication);
    }
}
