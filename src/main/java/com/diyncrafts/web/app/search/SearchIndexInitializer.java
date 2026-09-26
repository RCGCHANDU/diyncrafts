package com.diyncrafts.web.app.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.service.SearchIndexService;

/**
 * Creates the search index on startup. Elasticsearch being unavailable must not prevent the
 * application from starting, so failures are only logged.
 */
@Component
@ConditionalOnProperty(name = "app.search.initialize-index", havingValue = "true", matchIfMissing = true)
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final SearchIndexService searchIndexService;

    public SearchIndexInitializer(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            searchIndexService.ensureIndex();
        } catch (RuntimeException e) {
            log.warn("Could not initialise the search index; search is unavailable until Elasticsearch is reachable",
                    e);
        }
    }
}
