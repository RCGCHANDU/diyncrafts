package com.diyncrafts.web.app.repository.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import com.diyncrafts.web.app.model.VideoElasticSearch;

@Repository
public interface VideoElasticSearchRepository extends ElasticsearchRepository<VideoElasticSearch, Long> {
    // Custom query methods can be added here if needed
}