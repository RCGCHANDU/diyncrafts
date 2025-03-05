package com.diyncrafts.webapp.repository.es;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import com.diyncrafts.webapp.model.Video;

@Repository("searchElasticRepository")
public interface SearchRepository extends ElasticsearchRepository<Video, Long> {
    List<Video> findByTitleContainingOrDescriptionContaining(String title, String description);

    List<Video> findByCategory(String category);

    List<Video> findByDifficultyLevel(String difficultyLevel);

    List<Video> findByMaterialsUsedIn(List<String> materialsUsed);
}