package com.diyncrafts.web.app.service;

import java.util.List;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;


@Service
public class VideoSearchService {

    private final VideoElasticSearchRepository videoSearchRepository;

    public VideoSearchService(VideoElasticSearchRepository videoSearchRepository) {
        this.videoSearchRepository = videoSearchRepository;
    }

    // Basic search methods
    public List<VideoElasticSearch> searchByTitle(String title) {
        return videoSearchRepository.searchByTitleWildcard(title);
    }

    public List<VideoElasticSearch> searchByDescription(String description) {
        return videoSearchRepository.findByDescriptionContaining(description);
    }

    public List<VideoElasticSearch> searchByCategory(String categoryName) {
        return videoSearchRepository.findByCategoryName(categoryName);
    }

    public List<VideoElasticSearch> searchByDifficulty(String difficultyLevel) {
        return videoSearchRepository.findByDifficultyLevel(difficultyLevel);
    }

    public List<VideoElasticSearch> searchByUser(String userName) {
        return videoSearchRepository.findByUserName(userName);
    }

    public List<VideoElasticSearch> searchByMaterial(String material) {
        return videoSearchRepository.findByMaterialsUsedContains(material);
    }

    // Multi-field text search
    public List<VideoElasticSearch> searchByText(String searchText) {
        return videoSearchRepository.searchByText(searchText);
    }

    // Advanced search with multiple criteria
    public List<VideoElasticSearch> advancedSearch(
            String searchText, 
            String category, 
            String minDifficulty) {
        
        return videoSearchRepository.advancedSearch(searchText, category, minDifficulty);
    }

    // Example of combined search
    public List<VideoElasticSearch> searchWithFilters(
            String searchText, 
            String category, 
            String difficulty, 
            String material) {
        
        // Implement custom logic combining multiple criteria
        // For simplicity, call existing methods here
        if (category != null) {
            return searchByCategory(category);
        } else if (material != null) {
            return searchByMaterial(material);
        } else {
            return searchByText(searchText);
        }
    }
}