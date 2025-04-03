package com.diyncrafts.webapp.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

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

    @Field(type = FieldType.Date)
    @NotNull(message = "Upload date is required")
    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;

    @Field(type = FieldType.Long)
    @NotNull(message = "View count is required")
    @Column(name = "views", nullable = false)
    private Long viewCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    private Category category;

    @Field(type = FieldType.Keyword)
    @NotBlank(message = "Difficulty level is required")
    @Column(nullable = false)
    private String difficultyLevel;

    @Field(type = FieldType.Keyword)
    @NotBlank(message = "Video URL is required")
    @Column(nullable = false)
    private String videoUrl;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Field(type = FieldType.Keyword)
    @Column(name = "category_name")
    private String categoryName;

    @ElementCollection
    @Column(name = "material")
    @CollectionTable(name = "video_materials", joinColumns = @JoinColumn(name = "video_id"))
    @Field(type = FieldType.Keyword)
    private List<String> materialsUsed;

    // Ensure category name is kept in sync with actual category
    @PrePersist
    @PreUpdate
    protected void updateCategoryName() {
        this.categoryName = (category != null) ? category.getName() : null;
    }

    // Check user ownership with null safety
    public static void checkUserOwnership(Video video) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        String currentUsername = authentication.getName();

        User videoUser = video.getUser();
        if (videoUser == null || !videoUser.getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't own this video");
        }
    }
}