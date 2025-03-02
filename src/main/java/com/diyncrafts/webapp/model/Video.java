package com.diyncrafts.webapp.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String difficultyLevel;

    @Column(nullable = false)
    private String videoUrl; // URL of the video stored in AWS S3

    @Column(nullable = false)
    private Long userId; // ID of the user who uploaded the video
}