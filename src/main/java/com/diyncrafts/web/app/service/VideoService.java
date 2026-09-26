package com.diyncrafts.web.app.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.PageResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUpdateRequest;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.EditorPickRepository;
import com.diyncrafts.web.app.repository.jpa.GuideRepository;
import com.diyncrafts.web.app.repository.jpa.VideoDailyViewsRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.AccessGuard;
import com.diyncrafts.web.app.storage.ObjectKeys;
import com.diyncrafts.web.app.storage.ObjectStorageService;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;
    private final GuideRepository guideRepository;
    private final EditorPickRepository editorPickRepository;
    private final VideoDailyViewsRepository dailyViewsRepository;
    private final SearchIndexService searchIndex;
    private final ObjectStorageService storage;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public VideoService(VideoRepository videoRepository, CategoryRepository categoryRepository,
            GuideRepository guideRepository, EditorPickRepository editorPickRepository,
            VideoDailyViewsRepository dailyViewsRepository, SearchIndexService searchIndex,
            ObjectStorageService storage, UserService userService, AccessGuard accessGuard,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.videoRepository = videoRepository;
        this.categoryRepository = categoryRepository;
        this.guideRepository = guideRepository;
        this.editorPickRepository = editorPickRepository;
        this.dailyViewsRepository = dailyViewsRepository;
        this.searchIndex = searchIndex;
        this.storage = storage;
        this.userService = userService;
        this.accessGuard = accessGuard;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Updates metadata; the video binary is never replaced here. A new thumbnail is uploaded between
     * two short transactions so no database connection is held during the S3 transfer.
     */
    public VideoResponse updateVideo(Long id, VideoUpdateRequest request, Authentication authentication)
            throws IOException {
        transactions.executeWithoutResult(status ->
                accessGuard.requireOwnerOrAdmin(findVideo(id).getUser(), authentication, "video"));
        MultipartFile thumbnail = request.thumbnailFile();
        String thumbnailUrl = (thumbnail != null && !thumbnail.isEmpty())
                ? storage.storeImage(thumbnail, ObjectKeys::thumbnail)
                : null;
        Updated updated = transactions.execute(status -> {
            Video video = findVideo(id);
            accessGuard.requireOwnerOrAdmin(video.getUser(), authentication, "video");
            video.setTitle(request.title());
            video.setDescription(request.description());
            video.setDifficultyLevel(request.difficultyLevel());
            video.setCategory(resolveCategory(request.category()));
            if (request.materialsUsed() != null) {
                video.setMaterialsUsed(normalize(request.materialsUsed()));
            }
            if (thumbnailUrl != null) {
                video.setThumbnailUrl(thumbnailUrl);
            }
            return new Updated(VideoResponse.from(video), VideoElasticSearch.from(video));
        });
        // Index after commit: an Elasticsearch call must not hold the database transaction open.
        searchIndex.save(updated.document());
        log.info("Video {} updated by '{}'", id, authentication.getName());
        return updated.response();
    }

    private record Updated(VideoResponse response, VideoElasticSearch document) {
    }

    public void deleteVideo(Long id, Authentication authentication) {
        transactions.executeWithoutResult(status -> {
            Video video = findVideo(id);
            accessGuard.requireOwnerOrAdmin(video.getUser(), authentication, "video");
            // Guides and editor's picks reference the video and would otherwise block the delete.
            guideRepository.deleteByVideoId(id);
            editorPickRepository.deleteByVideoId(id);
            videoRepository.delete(video);
        });
        searchIndex.remove(id);
        log.info("Video {} deleted by '{}'", id, authentication.getName());
    }

    @Transactional(readOnly = true)
    public VideoResponse getVideo(Long id) {
        return VideoResponse.from(findVideo(id));
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getVideosOf(Authentication authentication) {
        return videoRepository.findVideosByUser(userService.currentUser(authentication).getId()).stream()
                .map(VideoResponse::from).toList();
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
        return videoRepository.findByDifficultyLevelOrderByIdDesc(difficultyLevel).stream()
                .map(VideoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> getTrendingVideos() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(30);
        return videoRepository.findTop5RecentByViewCount(cutoff, PageRequest.of(0, 5)).getContent().stream()
                .map(VideoResponse::from).toList();
    }

    /**
     * Counts a view both in the lifetime total and in the per-day statistics.
     */
    @Transactional
    public void logView(Long videoId) {
        if (videoRepository.incrementViewCount(videoId) == 0) {
            throw new ResourceNotFoundException("Video not found.");
        }
        dailyViewsRepository.incrementViews(videoId, LocalDate.now(clock));
    }

    Category resolveCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        Category category = categoryRepository.findByName(categoryName.trim());
        if (category == null) {
            throw new InvalidRequestException("Unknown category: " + categoryName.trim());
        }
        return category;
    }

    static List<String> normalize(List<String> materials) {
        return materials == null ? new ArrayList<>()
                : new ArrayList<>(materials.stream().map(String::trim).distinct().toList());
    }

    private Video findVideo(Long id) {
        return videoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Video not found."));
    }
}
