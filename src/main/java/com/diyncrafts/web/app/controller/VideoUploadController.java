package com.diyncrafts.web.app.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.ChunkedVideoUploadRequest;
import com.diyncrafts.web.app.dto.TaskResponse;
import com.diyncrafts.web.app.dto.VideoAndTaskResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.service.ChunkedVideoUploadService;
import com.diyncrafts.web.app.service.VideoUploadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/videos")
public class VideoUploadController {

    private final VideoUploadService videoUploadService;
    private final ChunkedVideoUploadService chunkedUploadService;

    public VideoUploadController(VideoUploadService videoUploadService,
            ChunkedVideoUploadService chunkedUploadService) {
        this.videoUploadService = videoUploadService;
        this.chunkedUploadService = chunkedUploadService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoAndTaskResponse upload(@Valid @ModelAttribute VideoUploadRequest request,
            Authentication authentication) throws IOException {
        return videoUploadService.upload(request, authentication);
    }

    @PostMapping(path = "/uploads/{uploadId}/chunks/{index}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadChunk(@PathVariable UUID uploadId, @PathVariable int index,
            @org.springframework.web.bind.annotation.RequestPart("chunk") MultipartFile chunk,
            Authentication authentication) throws IOException {
        chunkedUploadService.append(uploadId, index, chunk, authentication);
    }

    @PostMapping(path = "/uploads/{uploadId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoAndTaskResponse completeChunkedUpload(@PathVariable UUID uploadId,
            @Valid @ModelAttribute ChunkedVideoUploadRequest request, Authentication authentication)
            throws IOException {
        return chunkedUploadService.complete(uploadId, request, authentication);
    }

    @DeleteMapping("/uploads/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelChunkedUpload(@PathVariable UUID uploadId, Authentication authentication)
            throws IOException {
        chunkedUploadService.cancel(uploadId, authentication);
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

