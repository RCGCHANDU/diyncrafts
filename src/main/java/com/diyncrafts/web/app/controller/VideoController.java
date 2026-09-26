package com.diyncrafts.web.app.controller;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.PageResponse;
import com.diyncrafts.web.app.dto.VideoResponse;
import com.diyncrafts.web.app.dto.VideoUpdateRequest;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.service.VideoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    // Client-controlled sorting is limited to public fields (never e.g. user.password).
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "uploadDate", "viewCount");

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    public PageResponse<VideoResponse> getAllVideos(Pageable pageable) {
        return videoService.getVideos(withSafeSort(pageable));
    }

    @GetMapping("/{id}")
    public VideoResponse getVideoById(@PathVariable Long id) {
        return videoService.getVideo(id);
    }

    @GetMapping("/user")
    public List<VideoResponse> getAuthenticatedUserVideos(Authentication authentication) {
        return videoService.getVideosOf(authentication);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoResponse updateVideo(@PathVariable Long id, @Valid @ModelAttribute VideoUpdateRequest request,
            Authentication authentication) throws IOException {
        return videoService.updateVideo(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id, Authentication authentication) {
        videoService.deleteVideo(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/log-view")
    public ResponseEntity<Void> logView(@PathVariable Long id) {
        videoService.logView(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/category/{category}")
    public List<VideoResponse> getVideosByCategory(@PathVariable String category) {
        return videoService.getVideosByCategory(category);
    }

    @GetMapping("/difficulty/{difficultyLevel}")
    public List<VideoResponse> getVideosByDifficultyLevel(@PathVariable String difficultyLevel) {
        return videoService.getVideosByDifficultyLevel(difficultyLevel);
    }

    @GetMapping("/trending")
    public List<VideoResponse> getTrendingVideos() {
        return videoService.getTrendingVideos();
    }

    private static Pageable withSafeSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort property: " + order.getProperty());
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "id");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
