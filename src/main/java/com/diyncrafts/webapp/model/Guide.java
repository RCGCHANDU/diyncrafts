package com.diyncrafts.webapp.model;

import lombok.Data;
import jakarta.persistence.*;


@Data
@Entity
public class Guide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // Detailed step-by-step instructions

    @Column(nullable = false)
    private String videoId; // ID of the associated video

    private String imageUrl; // Optional image URL for the guide
}