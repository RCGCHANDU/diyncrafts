package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.dto.VideoUploadRequest;
import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.model.User;
import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.jpa.CategoryRepository;
import com.diyncrafts.webapp.repository.jpa.UserRepository;
import com.diyncrafts.webapp.repository.jpa.VideoRepository;

import com.diyncrafts.webapp.repository.es.SearchRepository;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private SearchRepository videoSearchRepository;

    @Autowired
    private S3AsyncClient s3Client;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public Video uploadVideo(VideoUploadRequest videoUploadRequest, Authentication authentication) throws IOException {
        MultipartFile videoFile = videoUploadRequest.getVideoFile();
        String contentType = videoFile.getContentType();
        String title = videoUploadRequest.getTitle();
        String description = videoUploadRequest.getDescription();
        String categoryName = videoUploadRequest.getCategory();
        String difficultyLevel = videoUploadRequest.getDifficultyLevel();
        MultipartFile thumbnailFile = videoUploadRequest.getThumbnailFile();

        byte[] videoBytes = videoFile.getBytes();

        String videoFileName = System.currentTimeMillis() + "_" + videoFile.getOriginalFilename();

        String thumbnailUrl;
        String thumbnailFileName;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String originalThumbnailFilename = thumbnailFile.getOriginalFilename();
            if (originalThumbnailFilename == null) {
                throw new IllegalArgumentException("Thumbnail file name cannot be null");
            }
            thumbnailFileName = System.currentTimeMillis() + "_" + originalThumbnailFilename;
            String thumbnailContentType = thumbnailFile.getContentType();
            thumbnailUrl = uploadBytesToS3(thumbnailFile.getBytes(), thumbnailFileName, thumbnailContentType);
        } else {
            BufferedImage thumbnailImage = extractThumbnailFromBytes(videoBytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnailImage, "jpg", baos);
            thumbnailFileName = System.currentTimeMillis() + "_thumbnail.jpg";
            byte[] thumbnailBytes = baos.toByteArray();
            thumbnailUrl = uploadBytesToS3(thumbnailBytes, thumbnailFileName, "image/jpeg");
        }

        String videoUrl = uploadBytesToS3(videoBytes, videoFileName, contentType);

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setDifficultyLevel(difficultyLevel);
        video.setVideoUrl(videoUrl);
        video.setThumbnailUrl(thumbnailUrl);
        video.setUploadDate(LocalDate.now());
        video.setUser(currentUser);
        video.setViewCount(0L); // Fixed view count initialization

        if (categoryName != null) {
            Category category = categoryRepository.findByName(categoryName);
            video.setCategory(category);
        }

        Video savedVideo = videoRepository.save(video);
        videoSearchRepository.save(savedVideo);
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
            // Upload new video to S3
            byte[] videoBytes = newVideoFile.getBytes();
            String videoContentType = newVideoFile.getContentType();
            String videoFileName = System.currentTimeMillis() + "_" + newVideoFile.getOriginalFilename();
            String newVideoUrl = uploadBytesToS3(videoBytes, videoFileName, videoContentType);
            existingVideo.setVideoUrl(newVideoUrl);

            // Extract new thumbnail from video content
            BufferedImage thumbnailImage = extractThumbnailFromBytes(videoBytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnailImage, "jpg", baos);
            byte[] thumbnailBytes = baos.toByteArray();
            String thumbnailFileName = System.currentTimeMillis() + "_thumbnail.jpg";
            String newThumbnailUrl = uploadBytesToS3(thumbnailBytes, thumbnailFileName, "image/jpeg");
            existingVideo.setThumbnailUrl(newThumbnailUrl);
        }

        // 6. Save changes and update search index
        Video updatedVideo = videoRepository.save(existingVideo);
        videoSearchRepository.save(updatedVideo);
        
        return updatedVideo;
    }

    private BufferedImage extractThumbnailFromBytes(byte[] videoBytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(videoBytes);
             FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream)) {
            grabber.start();
            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("Failed to extract thumbnail from video");
            }
            Java2DFrameConverter converter = new Java2DFrameConverter();
            return converter.getBufferedImage(frame);
        }
    }

    private String uploadBytesToS3(byte[] bytes, String fileName, String contentType) throws IOException {
        try {
            s3Client.putObject(
                    req -> req.bucket(bucketName)
                            .key(fileName)
                            .contentType(contentType),
                    AsyncRequestBody.fromBytes(bytes)
            ).join();
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
        } catch (Exception e) {
            throw new IOException("Failed to upload to S3", e);
        }
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