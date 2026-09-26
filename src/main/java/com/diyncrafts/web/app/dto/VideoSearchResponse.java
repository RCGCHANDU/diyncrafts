package com.diyncrafts.web.app.dto;

import java.util.List;

import com.diyncrafts.web.app.model.VideoElasticSearch;

/**
 * A search hit from the Elasticsearch video index.
 */
public record VideoSearchResponse(
        Long id,
        String title,
        String description,
        String difficultyLevel,
        String categoryName,
        String userName,
        List<String> materialsUsed,
        String thumbnailUrl) {

    public static VideoSearchResponse from(VideoElasticSearch doc) {
        return new VideoSearchResponse(doc.getId(), doc.getTitle(), doc.getDescription(), doc.getDifficultyLevel(),
                doc.getCategoryName(), doc.getUserName(),
                doc.getMaterialsUsed() == null ? List.of() : List.copyOf(doc.getMaterialsUsed()),
                doc.getThumbnailUrl());
    }
}
