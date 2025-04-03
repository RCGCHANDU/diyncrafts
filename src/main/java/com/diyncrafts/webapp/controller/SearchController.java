package com.diyncrafts.webapp.controller;

import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<Video>> searchVideos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "difficultyLevel", required = false) String difficultyLevel,
            @RequestParam(value = "materialsUsed", required = false) List<String> materialsUsed) {
        return ResponseEntity.ok(searchService.searchVideos(keyword, category, difficultyLevel, materialsUsed));
    }
}