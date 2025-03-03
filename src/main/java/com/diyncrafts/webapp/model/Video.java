package com.diyncrafts.webapp.model;

import lombok.Data;

import java.util.List;

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
    
    @NotNull(message = "User ID is required")
    @Column(nullable = false)
    private Long userId; // ID of the user who uploaded the video

    @Field(type = FieldType.Keyword)
    @ElementCollection // For storing a list of materials in the database
    private List<String> materialsUsed; // List of materials used in the video
}