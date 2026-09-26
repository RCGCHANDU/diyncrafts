package com.diyncrafts.web.app.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.VideoSearchResponse;
import com.diyncrafts.web.app.service.VideoSearchService;

@RestController
@RequestMapping("/api/videos/search")
public class VideoSearchController {

    private final VideoSearchService videoSearchService;

    public VideoSearchController(VideoSearchService videoSearchService) {
        this.videoSearchService = videoSearchService;
    }

    @GetMapping("/title/{title}")
    public List<VideoSearchResponse> searchByTitle(@PathVariable String title) {
        return videoSearchService.searchByTitle(title);
    }

    @GetMapping("/category/{category}")
    public List<VideoSearchResponse> searchByCategory(@PathVariable String category) {
        return videoSearchService.searchByCategory(category);
    }

    @GetMapping("/difficulty/{difficulty}")
    public List<VideoSearchResponse> searchByDifficulty(@PathVariable String difficulty) {
        return videoSearchService.searchByDifficulty(difficulty);
    }

    @GetMapping("/material/{material}")
    public List<VideoSearchResponse> searchByMaterial(@PathVariable String material) {
        return videoSearchService.searchByMaterial(material);
    }

    @GetMapping("/user/{userName}")
    public List<VideoSearchResponse> searchByUser(@PathVariable String userName) {
        return videoSearchService.searchByUser(userName);
    }

    @GetMapping("/text/{text}")
    public List<VideoSearchResponse> searchByText(@PathVariable String text) {
        return videoSearchService.searchByText(text);
    }

    @GetMapping("/advanced")
    public List<VideoSearchResponse> advancedSearch(@RequestParam(required = false) String searchText,
            @RequestParam(required = false) String category, @RequestParam(required = false) String minDifficulty) {
        return videoSearchService.advancedSearch(searchText, category, minDifficulty);
    }

    @GetMapping("/filters")
    public List<VideoSearchResponse> searchWithFilters(@RequestParam(required = false) String searchText,
            @RequestParam(required = false) String category, @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String material) {
        return videoSearchService.searchWithFilters(searchText, category, difficulty, material);
    }
}
