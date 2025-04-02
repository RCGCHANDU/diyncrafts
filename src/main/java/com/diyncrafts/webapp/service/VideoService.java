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