package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.es.SearchRepository;
import com.diyncrafts.webapp.repository.jpa.VideoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;

@Service
public class VideoService {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private SearchRepository videoSearchRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public Video uploadVideo(MultipartFile file, String title, String description, Long categoryId, String difficultyLevel) throws IOException {

        // Get current user ID from Spring Security context
        Long userId = getCurrentUserId();
        // Upload file to S3
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build(), RequestBody.fromBytes(file.getBytes()));

        // Save video metadata to database
        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setDifficultyLevel(difficultyLevel);
        video.setVideoUrl("https://" + bucketName + ".s3.amazonaws.com/" + fileName);

        // Associate video with category
        if (categoryId != null) {
            Category category = new Category();
            category.setId(categoryId);
            video.setCategory(category);
        }

        // Set user ID
        video.setUserId(userId);

        Video savedVideo = videoRepository.save(video);

        // Index video in Elasticsearch
        videoSearchRepository.save(savedVideo);

        return savedVideo;
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Video getVideoById(Long id) {
        return videoRepository.findById(id).orElseThrow(() -> new RuntimeException("Video not found"));
    }

    public void deleteVideo(Long id) {
        Video video = videoRepository.findById(id).orElseThrow(() -> new RuntimeException("Video not found"));

        // Check if the current user is the owner or an admin
        Long currentUserId = getCurrentUserId();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !video.getUserId().equals(currentUserId)) {
            throw new RuntimeException("You are not authorized to delete this video");
        }

        videoRepository.deleteById(id);
    }
    
    public List<Video> getVideosByCategory(String category) {
        return videoRepository.findByCategory(category);
    }

    public List<Video> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevel(difficultyLevel);
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return Long.parseLong(username); // Assuming username is the user ID
    }
}