package com.diyncrafts.web.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;

@Service
public class VideoService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private VideoElasticSearchRepository videoElasticSearchRepository;

    @Autowired
    private VideoStorageService storageService;
    
    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public Video uploadVideo(VideoUploadRequest videoUploadRequest, Authentication authentication) throws IOException {
        MultipartFile videoFile = videoUploadRequest.getVideoFile();
        String title = videoUploadRequest.getTitle();
        String description = videoUploadRequest.getDescription();
        String categoryName = videoUploadRequest.getCategory();
        String difficultyLevel = videoUploadRequest.getDifficultyLevel();
        MultipartFile thumbnailFile = videoUploadRequest.getThumbnailFile();

        Video video = new Video();
        String videoFileName = System.currentTimeMillis() + "_" + videoFile.getOriginalFilename();
        String videoUrl = storageService.upload(
            videoFile.getBytes(),
            videoFileName,
            videoFile.getContentType()
        );
        video.setVideoUrl(videoUrl);

        // Handle thumbnail
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String thumbnailUrl = storageService.upload(
                thumbnailFile.getBytes(),
                thumbnailFile.getOriginalFilename(),
                thumbnailFile.getContentType()
            );
            video.setThumbnailUrl(thumbnailUrl);
        } else {
            BufferedImage thumbnail = thumbnailService.extractThumbnail(videoFile.getBytes());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            byte[] thumbnailBytes = baos.toByteArray();
            String thumbnailFileName = System.currentTimeMillis() + "_thumbnail.jpg";
            String thumbnailUrl = storageService.upload(thumbnailBytes, thumbnailFileName, "image/jpeg");
            video.setThumbnailUrl(thumbnailUrl);
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
        VideoElasticSearch esvideo = new VideoElasticSearch(savedVideo);
        videoElasticSearchRepository.save(esvideo);
        return savedVideo;
    }

    public Video updateVideo(
        Long id, 
        VideoUploadRequest request, 
        MultipartFile newVideoFile, 
        Authentication authentication
    ) throws IOException {
        
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

        // 5. Handle new video file upload
        if (newVideoFile != null && !newVideoFile.isEmpty()) {
            storageService.delete(storageService.extractKeyFromUrl(existingVideo.getVideoUrl()));
            storageService.delete(storageService.extractKeyFromUrl(existingVideo.getThumbnailUrl()));
            
            // Upload new video and thumbnail
            String newVideoUrl = storageService.upload(
                newVideoFile.getBytes(),
                newVideoFile.getOriginalFilename(),
                newVideoFile.getContentType()
            );
            existingVideo.setVideoUrl(newVideoUrl);

            
            MultipartFile thumbnailFile = request.getThumbnailFile();
            // Handle thumbnail
            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                String thumbnailUrl = storageService.upload(
                    thumbnailFile.getBytes(),
                    thumbnailFile.getOriginalFilename(),
                    thumbnailFile.getContentType()
                );
                existingVideo.setThumbnailUrl(thumbnailUrl);
            } else {
                BufferedImage thumbnail = thumbnailService.extractThumbnail(newVideoFile.getBytes());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(thumbnail, "jpg", baos);
                byte[] thumbnailBytes = baos.toByteArray();
                String thumbnailFileName = System.currentTimeMillis() + "_thumbnail.jpg";
                String thumbnailUrl = storageService.upload(thumbnailBytes, thumbnailFileName, "image/jpeg");
                existingVideo.setThumbnailUrl(thumbnailUrl);
            }
        }

        // 6. Save changes and update search index
        Video updatedVideo = videoRepository.save(existingVideo);
        VideoElasticSearch esVideo = new VideoElasticSearch(updatedVideo);
        videoElasticSearchRepository.save(esVideo);
        
        return updatedVideo;
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Video getVideoById(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        videoRepository.incrementViewCount(video.getId());
        return video;
    }

    public void deleteVideo(Long id) {
        if (!videoRepository.existsById(id)) {
            throw new RuntimeException("Video not found");
        }
        videoRepository.deleteById(id);
    }

    public List<Video> getVideosByCategory(String categoryName) {
        return videoRepository.findByCategoryName(categoryName);
    }

    public List<Video> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevel(difficultyLevel);
    }
}