package com.diyncrafts.web.app.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.PageResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUpdateRequest;
import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.EditorPickRepository;
import com.diyncrafts.web.app.repository.jpa.GuideRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.AccessGuard;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;
    private final GuideRepository guideRepository;
    private final EditorPickRepository editorPickRepository;
    private final VideoElasticSearchRepository searchRepository;
    private final VideoS3StorageService storageService;
    private final ThumbnailService thumbnailService;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final String bucketName;

    public VideoService(VideoRepository videoRepository, CategoryRepository categoryRepository,
            GuideRepository guideRepository, EditorPickRepository editorPickRepository,
            VideoElasticSearchRepository searchRepository, VideoS3StorageService storageService,
            ThumbnailService thumbnailService, UserService userService, AccessGuard accessGuard,
            @Value("${aws.s3.bucketName}") String bucketName) {
        this.videoRepository = videoRepository;
        this.categoryRepository = categoryRepository;
        this.guideRepository = guideRepository;
        this.editorPickRepository = editorPickRepository;
        this.searchRepository = searchRepository;
        this.storageService = storageService;
        this.thumbnailService = thumbnailService;
        this.userService = userService;
        this.accessGuard = accessGuard;
        this.bucketName = bucketName;
    }

    @Transactional
    public Video createVideo(VideoUploadRequest request, Authentication authentication) throws IOException {
        User owner = userService.currentUser(authentication);
        MultipartFile videoFile = request.videoFile();
        MultipartFile thumbnailFile = request.thumbnailFile();

        Video video = new Video();
        video.setVideoUrl(String.format("https://%s.s3.amazonaws.com/%s", bucketName, video.getTitle()));
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            storageService.uploadFile(thumbnailFile.getInputStream(), thumbnailFile.getSize(),
                    thumbnailFile.getOriginalFilename());
            video.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s", bucketName, thumbnailFile.getOriginalFilename()));
        } else {
            BufferedImage thumbnail = thumbnailService.extractThumbnail(videoFile.getBytes());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            byte[] thumbnailBytes = baos.toByteArray();
            storageService.uploadFile(new ByteArrayInputStream(thumbnailBytes), thumbnailBytes.length, "image/jpeg");
            video.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s_thumbnail", bucketName, video.getTitle()));
        }
        video.setTitle(request.title());
        video.setDescription(request.description());
        video.setDifficultyLevel(request.difficultyLevel());
        video.setMaterialsUsed(copyOf(request.materialsUsed()));
        video.setCategory(resolveCategory(request.category()));
        video.setUploadDate(LocalDate.now());
        video.setUser(owner);
        video.setViewCount(0L);
        Video saved = videoRepository.save(video);
        index(saved);
        return saved;
    }

    @Transactional
    public VideoResponse updateVideo(Long id, VideoUpdateRequest request, Authentication authentication)
            throws IOException {
        Video video = findVideo(id);
        accessGuard.requireOwnerOrAdmin(video.getUser(), authentication, "video");
        video.setTitle(request.title());
        video.setDescription(request.description());
        video.setDifficultyLevel(request.difficultyLevel());
        video.setCategory(resolveCategory(request.category()));
        if (request.materialsUsed() != null) {
            video.setMaterialsUsed(copyOf(request.materialsUsed()));
        }
        MultipartFile thumbnailFile = request.thumbnailFile();
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            storageService.uploadFile(thumbnailFile.getInputStream(), thumbnailFile.getSize(),
                    thumbnailFile.getOriginalFilename());
            video.setThumbnailUrl(
                    String.format("https://%s.s3.amazonaws.com/%s", bucketName, thumbnailFile.getOriginalFilename()));
        }
        index(video);
        return VideoResponse.from(video);
    }

    @Transactional
    public void deleteVideo(Long id, Authentication authentication) {
        Video video = findVideo(id);
        accessGuard.requireOwnerOrAdmin(video.getUser(), authentication, "video");
        // Guides and editor's picks reference the video and would otherwise block the delete.
        guideRepository.deleteByVideoId(id);
        editorPickRepository.deleteByVideoId(id);
        videoRepository.delete(video);
        try {
            searchRepository.deleteById(id);
        } catch (RuntimeException e) {
            log.warn("Could not remove video {} from the search index", id, e);
        }
        log.info("Video {} deleted by '{}'", id, authentication.getName());
    }

    @Transactional(readOnly = true)
    public VideoResponse getVideo(Long id) {
        return VideoResponse.from(findVideo(id));
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getVideosOf(Authentication authentication) {
        User user = userService.currentUser(authentication);
        return videoRepository.findVideosByUser(user.getId()).stream().map(VideoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> getVideos(Pageable pageable) {
        return PageResponse.from(videoRepository.findAll(pageable), VideoResponse::from);
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getVideosByCategory(String categoryName) {
        return videoRepository.findByCategoryName(categoryName).stream().map(VideoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getVideosByDifficultyLevel(String difficultyLevel) {
        return videoRepository.findByDifficultyLevelOrderByIdDesc(difficultyLevel).stream().map(VideoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getTrendingVideos() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        return videoRepository.findTop5RecentByViewCount(cutoff, PageRequest.of(0, 5)).getContent().stream()
                .map(VideoResponse::from).toList();
    }

    @Transactional
    public void logView(Long videoId) {
        if (videoRepository.incrementViewCount(videoId) == 0) {
            throw new ResourceNotFoundException("Video not found.");
        }
    }

    private Video findVideo(Long id) {
        return videoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Video not found."));
    }

    private Category resolveCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        Category category = categoryRepository.findByName(categoryName.trim());
        if (category == null) {
            throw new InvalidRequestException("Unknown category: " + categoryName.trim());
        }
        return category;
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values.stream().map(String::trim).toList());
    }

    /**
     * The search index is derived data: an Elasticsearch outage must not fail the database write.
     */
    private void index(Video video) {
        try {
            VideoElasticSearch document = new VideoElasticSearch();
            document.syncWithVideoEntity(video);
            searchRepository.save(document);
        } catch (RuntimeException e) {
            log.warn("Could not index video {}; it will be missing from search until reindexed", video.getId(), e);
        }
    }
}
