package com.diyncrafts.web.app.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Document(indexName = "videos", createIndex = true)
public class VideoElasticSearch {
    @Id
    private Long id;
    private String title;
    private String description;
    private String difficultyLevel;
    private String categoryName; 
    private String userName; 
    private List<String> materialsUsed;

    public VideoElasticSearch(Video video) {
        this.id = video.getId();
        this.title = video.getTitle();
        this.description = video.getDescription();
        this.difficultyLevel = video.getDifficultyLevel();
        this.categoryName = video.getCategory().getName();
        this.userName = video.getUser().getUsername();
        this.materialsUsed = video.getMaterialsUsed();
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getDifficultyLevel() {
        return difficultyLevel;
    }
    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
    public String getCategoryName() {
        return categoryName;
    }
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public List<String> getMaterialsUsed() {
        return materialsUsed;
    }
    public void setMaterialsUsed(List<String> materialsUsed) {
        this.materialsUsed = materialsUsed;
    }
}
