package com.diyncrafts.web.app.repository.es;

import java.util.List;

import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.diyncrafts.web.app.model.VideoElasticSearch;

@Repository
public interface VideoElasticSearchRepository extends ElasticsearchRepository<VideoElasticSearch, Long> {

    // Basic search methods
    List<VideoElasticSearch> findByDescriptionContaining(String description);
    List<VideoElasticSearch> findByCategoryName(String categoryName);
    List<VideoElasticSearch> findByDifficultyLevel(String difficultyLevel);
    List<VideoElasticSearch> findByUserName(String userName);
    List<VideoElasticSearch> findByMaterialsUsedContains(String material);
    
    @Query("{\"bool\": {\"must\": {\"wildcard\": {\"title\": \"*?*\"}}}}")
    List<VideoElasticSearch> searchByTitleWildcard(@Param("title") String title);

    // Multi-field text search using Elasticsearch query DSL
    @Query("{\n" +
           "  \"multi_match\": {\n" +
           "    \"query\": \"?0\",\n" +
           "    \"fields\": [\"title^3\", \"description^2\", \"materialsUsed\"],\n" +
           "    \"fuzziness\": \"AUTO\"\n" +
           "  }\n" +
           "}")
    List<VideoElasticSearch> searchByText(String searchText);

    // Advanced search combining multiple criteria
    @Query("{\n" +
           "  \"bool\": {\n" +
           "    \"must\": [\n" +
           "      {\"match\": {\"categoryName\": \"?1\"}},\n" +
           "      {\"range\": {\"difficultyLevel\": {\"gte\": \"?2\"}}}\n" +
           "    ],\n" +
           "    \"should\": [\n" +
           "      {\"match\": {\"title\": \"?0\"}},\n" +
           "      {\"match\": {\"description\": \"?0\"}}\n" +
           "    ],\n" +
           "    \"minimum_should_match\": 1\n" +
           "  }\n" +
           "}")
    List<VideoElasticSearch> advancedSearch(String searchText, String category, String minDifficulty);
}