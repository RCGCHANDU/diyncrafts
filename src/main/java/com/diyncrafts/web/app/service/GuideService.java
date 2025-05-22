package com.diyncrafts.web.app.service;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.dto.GuideCreateRequest;
import com.diyncrafts.web.app.dto.GuideUpdateRequest;
import com.diyncrafts.web.app.model.Guide;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.GuideRepository;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GuideService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private GuideRepository guideRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private S3AsyncClient s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Guide createGuide(
            GuideCreateRequest guideCreateRequest,
            Authentication authentication) throws IOException {

        Video video = videoRepository.findById(guideCreateRequest.getVideoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        // Check user ownership of video (replace with actual user check)
        // Video.checkUserOwnership(video);

        logger.info("Looking up user with username: {}", authentication.getName());
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> {
                    logger.error("User with username {} not found", authentication.getName());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });
        

        Guide guide = new Guide();
        guide.setTitle(guideCreateRequest.getTitle());
        guide.setContent(guideCreateRequest.getContent());
        guide.setVideo(video);
        guide.setUser(currentUser);

        return guideRepository.save(guide);
    }

    @Transactional
    public Guide updateGuide(
            Long id,
            GuideUpdateRequest guideUpdateRequest,
            MultipartFile imageFile) throws IOException {

        Guide existingGuide = guideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guide not found"));

        // Check user ownership of guide
        Guide.checkUserOwnership(existingGuide);

        existingGuide.setTitle(guideUpdateRequest.getTitle());
        existingGuide.setContent(guideUpdateRequest.getContent());

        if (imageFile != null && !imageFile.isEmpty()) {
            String newImageUrl = uploadImage(imageFile);
            existingGuide.setImageUrl(newImageUrl);
        }

        return guideRepository.save(existingGuide);
    }

    public List<Guide> getGuidesByVideoId(Long videoId, int offset, int limit) {
        return guideRepository.findByVideoId(videoId, offset, limit);
    }

    @Transactional(readOnly = true)
    public Guide getGuideById(Long id) {
        return guideRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guide not found"));
    }

    public List<Guide> getGuides(int offset, int limit) {
        return guideRepository.findAll(); // Order: limit first, offset second
    }

    public List<Guide> getAuthenticatedUserGuides(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return guideRepository.findGuidesByUser(currentUser.getId());
    }

    @Transactional
    public void deleteGuide(Long id) {
        Guide guide = guideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guide not found"));

        // Check user ownership of guide
        Guide.checkUserOwnership(guide);

        guideRepository.deleteById(id);
    }

    private String uploadImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        String fileName = generateUniqueFileName(imageFile.getOriginalFilename());
        byte[] fileBytes = imageFile.getBytes();

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileName)
                            .build(),
                    AsyncRequestBody.fromBytes(fileBytes)).join();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload image", e);
        }

        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName);
    }

    private String generateUniqueFileName(String originalName) {
        return UUID.randomUUID() + "-" + originalName;
    }
}