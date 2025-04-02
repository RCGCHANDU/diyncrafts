package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Guide;
import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.jpa.GuideRepository;
import com.diyncrafts.webapp.repository.jpa.VideoRepository;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class GuideService {

    @Autowired
    private GuideRepository guideRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private S3AsyncClient s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public Guide createGuide(
            String title, 
            String content, 
            Long videoId, 
            MultipartFile imageFile) throws IOException {
        
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        Guide guide = new Guide();
        guide.setTitle(title);
        guide.setContent(content);
        guide.setVideo(video);

        if (imageFile != null && !imageFile.isEmpty()) {
            String fileName = generateUniqueFileName(imageFile.getOriginalFilename());
            
            // Correct S3AsyncClient usage
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build(),
                AsyncRequestBody.fromBytes(imageFile.getBytes())
            ).join(); // Wait for async operation to complete
            
            guide.setImageUrl(String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName));
        }

        return guideRepository.save(guide);
    }

    public List<Guide> getGuidesByVideoId(Long videoId, int offset, int limit) {
        return guideRepository.findByVideoId(videoId, offset, limit);
    }

    public Guide updateGuide(
            Long id, 
            String title, 
            String content, 
            MultipartFile imageFile) throws IOException {
        
        Guide existingGuide = guideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guide not found"));

        existingGuide.setTitle(title);
        existingGuide.setContent(content);

        if (imageFile != null && !imageFile.isEmpty()) {
            String fileName = generateUniqueFileName(imageFile.getOriginalFilename());
            
            // Correct S3AsyncClient usage
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build(),
                AsyncRequestBody.fromBytes(imageFile.getBytes())
            ).join(); // Wait for async operation to complete
            
            existingGuide.setImageUrl(String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName));
        }

        return guideRepository.save(existingGuide);
    }

    public void deleteGuide(Long id) {
        guideRepository.deleteById(id);
    }

    private String generateUniqueFileName(String originalName) {
        return System.currentTimeMillis() + "-" + originalName;
    }
}