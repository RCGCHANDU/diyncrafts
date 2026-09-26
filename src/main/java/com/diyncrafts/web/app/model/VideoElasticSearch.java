package com.diyncrafts.web.app.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.Getter;
import lombok.Setter;

/**
 * Search document for a video; derived from the database and rebuildable at any time
 * ({@code POST /api/admin/search/reindex}).
 * <p>
 * The index is named {@code videos_v2} because the old {@code videos} index used dynamic mappings
 * (analyzed text for category/difficulty) that are incompatible with the exact-match filters below.
 * Keyword fields use a lowercase normalizer, so filters are case-insensitive.
 */
@Getter
@Setter
@Document(indexName = VideoElasticSearch.INDEX, createIndex = false)
@Setting(settingPath = "/elasticsearch/videos-settings.json")
public class VideoElasticSearch {

    public static final String INDEX = "videos_v2";

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword, normalizer = "lowercase_normalizer")
    private String difficultyLevel;

    @Field(type = FieldType.Keyword, normalizer = "lowercase_normalizer")
    private String categoryName;

    @Field(type = FieldType.Keyword, normalizer = "lowercase_normalizer")
    private String userName;

    @Field(type = FieldType.Text)
    private List<String> materialsUsed;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    /**
     * Must be called while the video's associations are loaded (inside a transaction).
     */
    public static VideoElasticSearch from(Video video) {
        VideoElasticSearch document = new VideoElasticSearch();
        document.id = video.getId();
        document.title = video.getTitle();
        document.description = video.getDescription();
        document.difficultyLevel = video.getDifficultyLevel();
        document.categoryName = video.getCategory() != null ? video.getCategory().getName() : null;
        document.userName = video.getUser() != null ? video.getUser().getUsername() : null;
        document.materialsUsed = video.getMaterialsUsed() == null ? List.of() : new ArrayList<>(video.getMaterialsUsed());
        document.thumbnailUrl = video.getThumbnailUrl();
        return document;
    }
}
