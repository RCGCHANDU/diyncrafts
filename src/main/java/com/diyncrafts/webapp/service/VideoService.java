package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public Video uploadVideo(MultipartFile file, String title, String description, String category, String difficultyLevel) throws IOException {
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
        video.setCategory(category);
        video.setDifficultyLevel(difficultyLevel);
        video.setVideoUrl("https://" + bucketName + ".s3.amazonaws.com/" + fileName);
        return videoRepository.save(video);
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Video getVideoById(Long id) {
        return videoRepository.findById(id).orElseThrow(() -> new RuntimeException("Video not found"));
    }

    public void deleteVideo(Long id) {
        videoRepository.deleteById(id);
    }

    public List<Video> getVideosByCategory(String category) {
        return videoRepository.findByCategory(category);
    }

    public List<Video> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevel(difficultyLevel);
    }
}