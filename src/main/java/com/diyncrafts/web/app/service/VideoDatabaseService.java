package com.diyncrafts.web.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.VideoMetadata;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

@Service
public class VideoDatabaseService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private VideoElasticSearchRepository videoElasticSearchRepository;

    @Autowired
    private VideoS3StorageService storageService;

    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public Video createVideo(VideoMetadata videoUploadRequest, Authentication authentication) throws IOException {
        MultipartFile videoFile = videoUploadRequest.getVideoFile();
        String title = videoUploadRequest.getTitle();
        String description = videoUploadRequest.getDescription();
        String categoryName = videoUploadRequest.getCategory();
        String difficultyLevel = videoUploadRequest.getDifficultyLevel();
        MultipartFile thumbnailFile = videoUploadRequest.getThumbnailFile();

        Video video = new Video();
        video.setVideoUrl(String.format("https://%s.s3.amazonaws.com/%s", bucketName, video.getTitle()));

        // Handle thumbnail
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            storageService.uploadFile(
                    thumbnailFile.getInputStream(),
                    thumbnailFile.getSize(),
                    thumbnailFile.getOriginalFilename());
            video.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s", bucketName, thumbnailFile.getOriginalFilename()));
        } else {
            BufferedImage thumbnail = thumbnailService.extractThumbnail(videoFile.getBytes());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            byte[] thumbnailBytes = baos.toByteArray();
            // Convert byte array to InputStream
            InputStream thumbnailInputStream = new ByteArrayInputStream(thumbnailBytes);
            storageService.uploadFile(thumbnailInputStream, thumbnailBytes.length, "image/jpeg");
            video.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s_thumbnail", bucketName, video.getTitle()));
        }

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        video.setTitle(title);
        video.setDescription(description);
        video.setDifficultyLevel(difficultyLevel);
        video.setUploadDate(LocalDate.now());
        video.setUser(currentUser);
        video.setViewCount(0L); // Fixed view count initialization

        if (categoryName != null) {
            Category category = categoryRepository.findByName(categoryName);
            video.setCategory(category);
        }

        Video savedVideo = videoRepository.save(video);
        VideoElasticSearch videoIndex = new VideoElasticSearch();
        videoIndex.syncWithVideoEntity(savedVideo);
        videoElasticSearchRepository.save(videoIndex);
        return savedVideo;
    }

    public Video updateVideo(
            Long id,
            VideoMetadata request,
            Authentication authentication) throws IOException {

        // 1. Retrieve existing video
        Video existingVideo = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        // 2. Check ownership
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!existingVideo.getUser().equals(currentUser)) {
            throw new RuntimeException("Unauthorized to update this video");
        }

        // 3. Update metadata fields
        existingVideo.setTitle(request.getTitle());
        existingVideo.setDescription(request.getDescription());
        existingVideo.setDifficultyLevel(request.getDifficultyLevel());

        // 4. Update category
        String categoryName = request.getCategory();
        if (categoryName != null) {
            Category category = categoryRepository.findByName(categoryName);
            existingVideo.setCategory(category);
        }

        MultipartFile thumbnailFile = request.getThumbnailFile();
        // Handle thumbnail
        if (thumbnailFile != null) {
            storageService.uploadFile(
                    thumbnailFile.getInputStream(),
                    thumbnailFile.getSize(),
                    thumbnailFile.getOriginalFilename());
            existingVideo.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s", bucketName, thumbnailFile.getOriginalFilename()));
        }

        // 6. Save changes and update search index
        Video updatedVideo = videoRepository.save(existingVideo);

        VideoElasticSearch videoIndex = new VideoElasticSearch();
        videoIndex.syncWithVideoEntity(updatedVideo);
        videoElasticSearchRepository.save(videoIndex);
        return updatedVideo;
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public List<Video> getAuthenticatedUserVideos(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return videoRepository.findVideosByUser(currentUser.getId());
    }

    public Video getVideoById(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        videoRepository.incrementViewCount(video.getId());
        videoRepository.save(video);
        return video;
    }

    public void deleteVideo(Long id) {
        if (!videoRepository.existsById(id)) {
            throw new RuntimeException("Video not found");
        }
        videoRepository.deleteById(id);
        videoElasticSearchRepository.deleteById(id);
    }

    public List<Video> getVideosByCategory(String categoryName) {
        return videoRepository.findByCategoryName(categoryName);
    }

    public List<Video> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevel(difficultyLevel);
    }

    public List<Video> getTrendingVideos() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        Pageable top5 = PageRequest.of(0, 5); // Top 5 results

        return videoRepository.findTop5RecentByViewCount(cutoff, top5).getContent();
    }
}