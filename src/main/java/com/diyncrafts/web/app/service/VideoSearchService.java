package com.diyncrafts.web.app.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.dto.VideoSearchResponse;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;

@Service
public class VideoSearchService {

    private final VideoElasticSearchRepository videoSearchRepository;

    public VideoSearchService(VideoElasticSearchRepository videoSearchRepository) {
        this.videoSearchRepository = videoSearchRepository;
    }

    public List<VideoSearchResponse> searchByTitle(String title) {
        return map(videoSearchRepository.searchByTitleWildcard(title));
    }

    public List<VideoSearchResponse> searchByCategory(String categoryName) {
        return map(videoSearchRepository.findByCategoryName(categoryName));
    }

    public List<VideoSearchResponse> searchByDifficulty(String difficultyLevel) {
        return map(videoSearchRepository.findByDifficultyLevel(difficultyLevel));
    }

    public List<VideoSearchResponse> searchByUser(String userName) {
        return map(videoSearchRepository.findByUserName(userName));
    }

    public List<VideoSearchResponse> searchByMaterial(String material) {
        return map(videoSearchRepository.findByMaterialsUsedContains(material));
    }

    public List<VideoSearchResponse> searchByText(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return List.of();
        }
        return map(videoSearchRepository.searchByText(searchText));
    }

    public List<VideoSearchResponse> advancedSearch(String searchText, String category, String minDifficulty) {
        return map(videoSearchRepository.advancedSearch(searchText, category, minDifficulty));
    }

    public List<VideoSearchResponse> searchWithFilters(String searchText, String category, String difficulty,
            String material) {
        return map(videoSearchRepository.advancedSearch(searchText, category, difficulty));
    }

    private static List<VideoSearchResponse> map(List<VideoElasticSearch> documents) {
        return documents.stream().map(VideoSearchResponse::from).toList();
    }
}
