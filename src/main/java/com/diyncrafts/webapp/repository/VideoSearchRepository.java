package com.diyncrafts.webapp.repository;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.diyncrafts.webapp.model.Video;

public interface VideoSearchRepository extends ElasticsearchRepository<Video, Long> {
    List<Video> findByTitleContainingOrDescriptionContaining(String title, String description);

    List<Video> findByCategory(String category);

    List<Video> findByDifficultyLevel(String difficultyLevel);

    List<Video> findByMaterialsUsedIn(List<String> materialsUsed);
}