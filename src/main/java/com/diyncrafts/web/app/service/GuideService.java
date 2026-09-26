package com.diyncrafts.web.app.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.GuideCreateRequest;
import com.diyncrafts.web.app.dto.GuideResponse;
import com.diyncrafts.web.app.dto.GuideUpdateRequest;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.exceptions.StorageException;
import com.diyncrafts.web.app.model.Guide;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.GuideRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.AccessGuard;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class GuideService {

    private static final Logger log = LoggerFactory.getLogger(GuideService.class);

    private final GuideRepository guideRepository;
    private final VideoRepository videoRepository;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final S3AsyncClient s3Client;
    private final String bucketName;

    public GuideService(GuideRepository guideRepository, VideoRepository videoRepository, UserService userService,
            AccessGuard accessGuard, S3AsyncClient s3Client, @Value("${aws.s3.bucketName}") String bucketName) {
        this.guideRepository = guideRepository;
        this.videoRepository = videoRepository;
        this.userService = userService;
        this.accessGuard = accessGuard;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Transactional
    public GuideResponse createGuide(GuideCreateRequest request, Authentication authentication) {
        User author = userService.currentUser(authentication);
        // Any authenticated user may write a guide for any video (the original ownership check was
        // intentionally disabled).
        Video video = videoRepository.findById(request.videoId())
                .orElseThrow(() -> new ResourceNotFoundException("Video not found."));
        Guide guide = new Guide();
        guide.setTitle(request.title());
        guide.setContent(request.content());
        guide.setVideo(video);
        guide.setUser(author);
        return GuideResponse.from(guideRepository.save(guide));
    }

    @Transactional
    public GuideResponse updateGuide(Long id, GuideUpdateRequest request, MultipartFile imageFile,
            Authentication authentication) throws IOException {
        Guide guide = findGuide(id);
        accessGuard.requireOwnerOrAdmin(guide.getUser(), authentication, "guide");
        guide.setTitle(request.title());
        guide.setContent(request.content());
        if (imageFile != null && !imageFile.isEmpty()) {
            guide.setImageUrl(uploadImage(imageFile));
        }
        return GuideResponse.from(guide);
    }

    @Transactional(readOnly = true)
    public GuideResponse getGuide(Long id) {
        return GuideResponse.from(findGuide(id));
    }

    /**
     * @param page 1-based page number (the API has always used 1-based guide pages)
     */
    @Transactional(readOnly = true)
    public List<GuideResponse> getGuides(int page, int size) {
        return guideRepository.findAll(pageRequest(page, size)).map(GuideResponse::from).getContent();
    }

    @Transactional(readOnly = true)
    public List<GuideResponse> getGuidesByVideo(Long videoId, int page, int size) {
        return guideRepository.findByVideoId(videoId, pageRequest(page, size)).stream()
                .map(GuideResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GuideResponse> getGuidesOf(Authentication authentication) {
        User user = userService.currentUser(authentication);
        return guideRepository.findByUserIdOrderByIdAsc(user.getId()).stream().map(GuideResponse::from).toList();
    }

    @Transactional
    public void deleteGuide(Long id, Authentication authentication) {
        Guide guide = findGuide(id);
        accessGuard.requireOwnerOrAdmin(guide.getUser(), authentication, "guide");
        guideRepository.delete(guide);
    }

    private Guide findGuide(Long id) {
        return guideRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Guide not found."));
    }

    private static Pageable pageRequest(int page, int size) {
        return PageRequest.of(page - 1, size, Sort.by("id"));
    }

    private String uploadImage(MultipartFile imageFile) throws IOException {
        String key = UUID.randomUUID() + "-" + imageFile.getOriginalFilename();
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    AsyncRequestBody.fromBytes(imageFile.getBytes())).join();
        } catch (RuntimeException e) {
            throw new StorageException("Failed to upload guide image", e);
        }
        log.info("Uploaded guide image {}", key);
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }
}
