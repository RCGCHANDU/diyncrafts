package com.diyncrafts.web.app.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.VideoSearchCriteria;
import com.diyncrafts.web.app.dto.VideoSearchResponse;
import com.diyncrafts.web.app.service.VideoSearchService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Elasticsearch-backed search. All endpoints accept {@code page} (0-based, default 0) and {@code size}
 * (1-100, default 20) and return hits ordered by relevance, then newest first.
 */
@RestController
@RequestMapping("/api/videos/search")
public class VideoSearchController {

    private static final VideoSearchCriteria NONE = VideoSearchCriteria.empty();

    private final VideoSearchService videoSearchService;

    public VideoSearchController(VideoSearchService videoSearchService) {
        this.videoSearchService = videoSearchService;
    }

    @GetMapping("/title/{title}")
    public List<VideoSearchResponse> searchByTitle(@PathVariable @Size(max = 200) String title,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withTitle(title), page, size);
    }

    @GetMapping("/category/{category}")
    public List<VideoSearchResponse> searchByCategory(@PathVariable @Size(max = 100) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withCategory(category), page, size);
    }

    @GetMapping("/difficulty/{difficulty}")
    public List<VideoSearchResponse> searchByDifficulty(@PathVariable @Size(max = 50) String difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withDifficulty(difficulty), page, size);
    }

    @GetMapping("/material/{material}")
    public List<VideoSearchResponse> searchByMaterial(@PathVariable @Size(max = 100) String material,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withMaterial(material), page, size);
    }

    @GetMapping("/user/{userName}")
    public List<VideoSearchResponse> searchByUser(@PathVariable @Size(max = 50) String userName,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withUser(userName), page, size);
    }

    @GetMapping("/text/{text}")
    public List<VideoSearchResponse> searchByText(@PathVariable @Size(max = 200) String text,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withText(text), page, size);
    }

    /**
     * {@code minDifficulty} is kept for compatibility but is an exact difficulty filter: difficulty
     * levels are free text and have no defined order.
     */
    @GetMapping("/advanced")
    public List<VideoSearchResponse> advancedSearch(
            @RequestParam(required = false) @Size(max = 200) String searchText,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 50) String minDifficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withText(searchText).withCategory(category).withDifficulty(minDifficulty), page, size);
    }

    @GetMapping("/filters")
    public List<VideoSearchResponse> searchWithFilters(
            @RequestParam(required = false) @Size(max = 200) String searchText,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 50) String difficulty,
            @RequestParam(required = false) @Size(max = 100) String material,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search(NONE.withText(searchText).withCategory(category).withDifficulty(difficulty)
                .withMaterial(material), page, size);
    }

    private List<VideoSearchResponse> search(VideoSearchCriteria criteria, int page, int size) {
        return videoSearchService.search(criteria, PageRequest.of(page, size));
    }
}
