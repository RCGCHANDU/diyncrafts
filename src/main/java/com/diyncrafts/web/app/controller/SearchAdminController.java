package com.diyncrafts.web.app.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.service.SearchIndexService;

@RestController
@RequestMapping("/api/admin/search")
public class SearchAdminController {

    private final SearchIndexService searchIndexService;

    public SearchAdminController(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    /**
     * Rebuilds the search index from the database (e.g. after the index was lost or changed).
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> reindex() {
        return Map.of("indexed", searchIndexService.reindexAll());
    }
}
