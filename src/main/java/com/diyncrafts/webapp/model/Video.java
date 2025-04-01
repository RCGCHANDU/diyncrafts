package com.diyncrafts.webapp.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
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
    @jakarta.persistence.Id
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
    private Integer viewCount;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @Field(type = FieldType.Keyword)
    private Category category;

    @Field(type = FieldType.Keyword)
    @NotBlank(message = "Difficulty level is required")
    @Column(nullable = false)
    private String difficultyLevel;

    @Column(nullable = false)
    private String videoUrl; // URL of the video stored in AWS S3
    
    private UUID userId; // Foreign key to User

    @Column(name = "uploader_username", nullable = false)
    private String uploader;

    @Field(type = FieldType.Keyword)
    @ElementCollection // For storing a list of materials in the database
    private List<String> materialsUsed; // List of materials used in the video
}