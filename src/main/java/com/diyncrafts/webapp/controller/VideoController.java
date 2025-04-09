package com.diyncrafts.webapp.controller;

import com.diyncrafts.webapp.dto.VideoUploadRequest;
import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.service.VideoService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Video> uploadVideo(
        @RequestPart("videoFile") MultipartFile videoFile,
        @Valid @ModelAttribute VideoUploadRequest videoUploadRequest
    ) throws IOException {
        
        // 1. Log that the request was received
        logger.info("Received upload request for video: {}", videoUploadRequest.getTitle());
        
        // 2. Log file details (to confirm it's present)
        if (videoFile != null) {
            logger.debug("File received: name={}, size={} bytes", videoFile.getOriginalFilename(), videoFile.getSize());
        } else {
            logger.warn("No file attached to the request.");
        }

        // 3. Log authentication details (to confirm security context)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.info("User '{}' is attempting to upload a video", authentication.getName());

        // 4. Log before returning the response
        Video uploadedVideo = videoService.uploadVideo(videoUploadRequest, authentication);
        logger.info("Video uploaded successfully: ID={}", uploadedVideo.getId());

        return ResponseEntity.ok(uploadedVideo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Video> updateVideo(
        @PathVariable Long id,
        @RequestPart("videoFile") MultipartFile videoFile,
        @Valid @ModelAttribute VideoUploadRequest videoUploadRequest
    ) throws IOException {
        
        // 1. Log the update request
        logger.info("Received update request for video ID: {}", id);
        logger.info("Update details: title '{}', category '{}'", 
            videoUploadRequest.getTitle(), videoUploadRequest.getCategory());
        
        // 2. Log file details (if present)
        if (videoFile != null) {
            logger.debug("New file provided: name={}, size={} bytes", 
                videoFile.getOriginalFilename(), videoFile.getSize());
        } else {
            logger.debug("No new file attached in the update request.");
        }

        // 3. Log authentication details
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.info("User '{}' is updating video ID={}", authentication.getName(), id);

        // 4. Perform the update through the service
        Video updatedVideo = videoService.updateVideo(
            id, 
            videoUploadRequest, 
            videoFile, 
            authentication
        );

        // 5. Log success and return response
        logger.info("Video updated successfully: ID={}", updatedVideo.getId());
        return ResponseEntity.ok(updatedVideo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id) {
        videoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Video>> getAllVideos() {
        return ResponseEntity.ok(videoService.getAllVideos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Video> getVideoById(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.getVideoById(id));
    }


    @GetMapping("/category/{category}")
    public ResponseEntity<List<Video>> getVideosByCategory(@PathVariable String category) {
        return ResponseEntity.ok(videoService.getVideosByCategory(category));
    }

    @GetMapping("/difficulty/{difficultyLevel}")
    public ResponseEntity<List<Video>> getVideosByDifficultyLevel(@PathVariable String difficultyLevel) {
        return ResponseEntity.ok(videoService.getVideosByDifficultyLevel(difficultyLevel));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception e) {
        logger.error("An unexpected error occurred: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("An unexpected error occurred: " + e.getMessage());
    }
}