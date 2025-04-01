package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.dto.VideoUploadRequest;
import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.model.User;
import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.es.SearchRepository;
import com.diyncrafts.webapp.repository.jpa.UserRepository;
import com.diyncrafts.webapp.repository.jpa.VideoRepository;

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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;

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

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public Video uploadVideo(VideoUploadRequest videoUploadRequest, Authentication authentication) throws IOException {
        MultipartFile videoFile = videoUploadRequest.getFile();
        String contentType = videoFile.getContentType(); // Capture MIME type here
        String title = videoUploadRequest.getTitle();
        String description = videoUploadRequest.getDescription();
        Long categoryId = videoUploadRequest.getCategoryId();
        String difficultyLevel = videoUploadRequest.getDifficultyLevel();
        MultipartFile thumbnailFile = videoUploadRequest.getThumbnailFile(); // Get the provided thumbnail

        // Read the file into a byte array (store it in memory)
        byte[] videoBytes = videoFile.getBytes(); // Read once here
        
        // Generate filenames
        String videoFileName = System.currentTimeMillis() + "_" + videoFile.getOriginalFilename();
        
        // Handle thumbnail logic
        String thumbnailUrl;
        String thumbnailFileName;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            // Use the provided thumbnail
            String originalThumbnailFilename = thumbnailFile.getOriginalFilename();
            if (originalThumbnailFilename == null) {
                throw new IllegalArgumentException("Thumbnail file name cannot be null");
            }
            thumbnailFileName = System.currentTimeMillis() + "_" + originalThumbnailFilename;
            String thumbnailContentType = thumbnailFile.getContentType();
            thumbnailUrl = uploadBytesToS3(thumbnailFile.getBytes(), thumbnailFileName, thumbnailContentType);
        } else {
            // Extract thumbnail from video
            BufferedImage thumbnailImage = extractThumbnailFromBytes(videoBytes);
            // Convert BufferedImage to byte[] for S3 upload
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnailImage, "jpg", baos);
            thumbnailFileName = System.currentTimeMillis() + "_thumbnail.jpg";
            byte[] thumbnailBytes = baos.toByteArray();
            thumbnailUrl = uploadBytesToS3(thumbnailBytes, thumbnailFileName, "image/jpeg");
        }

        // Save video to S3
        String videoUrl = uploadBytesToS3(videoBytes, videoFileName, contentType); // Existing method
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found"));

        // Create Video entity
        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setDifficultyLevel(difficultyLevel);
        video.setVideoUrl(videoUrl);
        video.setThumbnailUrl(thumbnailUrl);
        video.setUploadDate(LocalDate.now());
        video.setViewCount(0);
        video.setUserId(currentUser.getId());
        video.setUploader(currentUser.getUsername());

        // Set category (existing logic)
        if (categoryId != null) {
            Category category = new Category();
            category.setId(categoryId);
            video.setCategory(category);
        }

        // Save to repositories
        Video savedVideo = videoRepository.save(video);
        videoSearchRepository.save(savedVideo);
        return savedVideo;
    }

    private BufferedImage extractThumbnailFromBytes(byte[] videoBytes) throws IOException {        
        File tempVideoFile = File.createTempFile("video", ".mp4");
        tempVideoFile.deleteOnExit(); // Delete only when JVM exits (or manually)
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Save the video to a temporary file (required for JavaCV)
            try (FileOutputStream fos = new FileOutputStream(tempVideoFile)) {
                fos.write(videoBytes);
            }
    
            // Use JavaCV to extract a frame (automatically closes the grabber)
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tempVideoFile.getAbsolutePath())) {
                grabber.start();
                Frame frame = grabber.grabImage(); // Extract first frame (adjust timing if needed)
    
                if (frame != null) {
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage image = converter.getBufferedImage(frame);
                    
                    // Resize the image (optional)
                    // image = resizeImage(image, 300, 200); // Implement resizeImage() if needed
                    
                    return image;
                } else {
                    throw new IOException("Failed to extract thumbnail from video");
                }
            }
        } finally {
            // Clean up temporary file
            if (tempVideoFile != null && tempVideoFile.exists()) {
                tempVideoFile.delete();
            }
        }
    }

     // Helper method to upload bytes to S3
    private String uploadBytesToS3(byte[] bytes, String fileName, String contentType) throws IOException {
        s3Client.putObject(
            req -> req.bucket(bucketName)
                     .key(fileName)
                     .contentType(contentType),
            AsyncRequestBody.fromBytes(bytes)
        ).join();
    
        // Return the URL after the upload completes
        return "https://" + bucketName + ".s3.eu-west-2.amazonaws.com/" + fileName;
    }

    // Helper method to upload files to S3
    private String uploadFileToS3(MultipartFile file, String fileName) throws IOException{
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        s3Client.putObject(
            req -> req.bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType),
            AsyncRequestBody.fromInputStream(
                file.getInputStream(),
                file.getSize(),
                Executors.newSingleThreadExecutor()
            )
        ).join();
        return "https://" + bucketName + ".s3.eu-west-2.amazonaws.com/" + fileName;
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Video getVideoById(Long id) {
        Video video = videoRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Video not found"));

        // Increment the view count
        videoRepository.incrementViewCount(video.getId());

        return video;
    }

    public void deleteVideo(Long id) {
        if (videoRepository.existsById(id)) {
            videoRepository.deleteById(id);
        } else {
            throw new RuntimeException("Video not exists");
        }
    }
    
    public List<Video> getVideosByCategory(String category) {
        return videoRepository.findByCategoryName(category);
    }

    public List<Video> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevel(difficultyLevel);
    }
}