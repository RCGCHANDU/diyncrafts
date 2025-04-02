package com.diyncrafts.webapp.model;

import lombok.Data;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;


@Data
@Entity
public class Guide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Content is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "video_id", nullable = false)
    private Video video; 

    @Column(name = "image_url", nullable = true)
    private String imageUrl;
}