package com.diyncrafts.web.app.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.dto.VideoSearchCriteria;
import com.diyncrafts.web.app.dto.VideoSearchResponse;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.search.VideoSearchQueries;

@Service
public class VideoSearchService {

    private final ElasticsearchOperations operations;

    public VideoSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public List<VideoSearchResponse> search(VideoSearchCriteria criteria, Pageable pageable) {
        return operations.search(VideoSearchQueries.build(criteria, pageable), VideoElasticSearch.class)
                .getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(VideoSearchResponse::from)
                .toList();
    }
}
