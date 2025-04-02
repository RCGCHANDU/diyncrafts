package com.diyncrafts.webapp.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Entity
@Document(indexName = "videos")
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Field(type = FieldType.Text)
    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @Field(type = FieldType.Text)
    @NotBlank(message = "Description is required")
    @Column(nullable = false)
    private String description;

    @Field(type = FieldType.Keyword)
    @NotBlank(message = "Thumbnail URL is required")
    @Column(nullable = false)
    private String thumbnailUrl;

    @NotNull(message = "Upload date is required")
    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;

    @NotNull(message = "View count is required")
    @Column(name = "views", nullable = false)
    private Long viewCount;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = true)
    @Field(type = FieldType.Keyword) // Indexes category name via getter
    private Category category;

    @Field(type = FieldType.Keyword)
    @NotBlank(message = "Difficulty level is required")
    @Column(nullable = false)
    private String difficultyLevel;

    @NotBlank(message = "Video URL is required")
    @Column(nullable = false)
    private String videoUrl;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; 

    @Field(type = FieldType.Keyword) // For Elasticsearch indexing
    private String categoryName; 

    @ElementCollection
    @Column(name = "material")
    @CollectionTable(name = "video_materials", joinColumns = @JoinColumn(name = "video_id"))
    private List<String> materialsUsed;

    // Add getter for category name for Elasticsearch indexing
    @Transient
    @Field(type = FieldType.Keyword)
    public String getCategoryName() {
        return category != null ? category.getName() : null;
    }
}