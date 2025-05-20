package com.diyncrafts.web.app.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;


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
        if (searchText == null || searchText.trim().isEmpty()) {
            return Collections.emptyList(); // Or throw a custom exception
        }
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
        
        return videoSearchRepository.advancedSearch(searchText, category, difficulty);
    }
}