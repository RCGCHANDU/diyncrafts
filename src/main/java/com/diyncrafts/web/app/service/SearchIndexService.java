package com.diyncrafts.web.app.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

/**
 * Keeps the Elasticsearch index in sync with the database. The index is derived data, so write
 * failures are logged instead of failing the user's request; {@link #reindexAll()} repairs drift.
 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final int REINDEX_BATCH = 500;

    private final VideoElasticSearchRepository repository;
    private final ElasticsearchOperations operations;
    private final VideoRepository videoRepository;
    private final TransactionTemplate readOnly;

    public SearchIndexService(VideoElasticSearchRepository repository, ElasticsearchOperations operations,
            VideoRepository videoRepository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.operations = operations;
        this.videoRepository = videoRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public void save(VideoElasticSearch document) {
        try {
            repository.save(document);
        } catch (RuntimeException e) {
            log.warn("Could not index video {}; it will be missing from search until reindexed",
                    document.getId(), e);
        }
    }

    public void remove(Long videoId) {
        try {
            repository.deleteById(videoId);
        } catch (RuntimeException e) {
            log.warn("Could not remove video {} from the search index", videoId, e);
        }
    }

    /**
     * Creates the index with its explicit mapping if it does not exist yet.
     */
    public void ensureIndex() {
        IndexOperations index = operations.indexOps(VideoElasticSearch.class);
        if (!index.exists()) {
            index.createWithMapping();
            log.info("Created search index {}", VideoElasticSearch.INDEX);
        }
    }

    /**
     * Rebuilds every document from the database. Failures propagate (this is an explicit admin action).
     *
     * @return number of indexed videos
     */
    public long reindexAll() {
        ensureIndex();
        long indexed = 0;
        int page = 0;
        while (true) {
            int current = page;
            List<VideoElasticSearch> batch = readOnly.execute(status -> {
                Page<Video> videos = videoRepository.findAll(PageRequest.of(current, REINDEX_BATCH, Sort.by("id")));
                return videos.getContent().stream().map(VideoElasticSearch::from).toList();
            });
            if (batch == null || batch.isEmpty()) {
                break;
            }
            repository.saveAll(batch);
            indexed += batch.size();
            page++;
        }
        log.info("Reindexed {} videos into {}", indexed, VideoElasticSearch.INDEX);
        return indexed;
    }
}
