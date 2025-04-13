package com.diyncrafts.web.app.controller;

import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.service.VideoSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoSearchController {

    private final VideoSearchService videoSearchService;

    public VideoSearchController(VideoSearchService videoSearchService) {
        this.videoSearchService = videoSearchService;
    }

    // Basic search endpoints
    @GetMapping("/search/title/{title}")
    public ResponseEntity<List<VideoElasticSearch>> searchByTitle(@PathVariable String title) {
        return ResponseEntity.ok(videoSearchService.searchByTitle(title));
    }

    @GetMapping("/search/category/{category}")
    public ResponseEntity<List<VideoElasticSearch>> searchByCategory(@PathVariable String category) {
        return ResponseEntity.ok(videoSearchService.searchByCategory(category));
    }

    @GetMapping("/search/difficulty/{difficulty}")
    public ResponseEntity<List<VideoElasticSearch>> searchByDifficulty(@PathVariable String difficulty) {
        return ResponseEntity.ok(videoSearchService.searchByDifficulty(difficulty));
    }

    @GetMapping("/search/material/{material}")
    public ResponseEntity<List<VideoElasticSearch>> searchByMaterial(@PathVariable String material) {
        return ResponseEntity.ok(videoSearchService.searchByMaterial(material));
    }

    @GetMapping("/search/user/{userName}")
    public ResponseEntity<List<VideoElasticSearch>> searchByUser(@PathVariable String userName) {
        return ResponseEntity.ok(videoSearchService.searchByUser(userName));
    }

    // Text search endpoint
    @GetMapping("/search/text/{text}")
    public ResponseEntity<List<VideoElasticSearch>> searchByText(@PathVariable String text) {
        return ResponseEntity.ok(videoSearchService.searchByText(text));
    }

    // Advanced search endpoint
    @GetMapping("/search/advanced")
    public ResponseEntity<List<VideoElasticSearch>> advancedSearch(
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String minDifficulty
    ) {
        return ResponseEntity.ok(videoSearchService.advancedSearch(searchText, category, minDifficulty));
    }

    // Combined search endpoint
    @GetMapping("/search/filters")
    public ResponseEntity<List<VideoElasticSearch>> searchWithFilters(
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String material
    ) {
        return ResponseEntity.ok(videoSearchService.searchWithFilters(searchText, category, difficulty, material));
    }
}