package com.diyncrafts.web.app.repository.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.diyncrafts.web.app.model.VideoElasticSearch;

/**
 * Document CRUD only; queries are built explicitly in {@code VideoSearchQueries}.
 */
public interface VideoElasticSearchRepository extends ElasticsearchRepository<VideoElasticSearch, Long> {
}
