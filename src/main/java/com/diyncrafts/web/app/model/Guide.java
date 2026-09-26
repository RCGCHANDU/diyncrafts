package com.diyncrafts.web.app.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


import jakarta.persistence.*;


@Getter
@Setter
@Entity
public class Guide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    // Many guides per author (was @OneToOne, which limited each user to a single guide).
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @OneToMany(mappedBy = "guide", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepNumber ASC")
    private List<Step> steps;

    @Override
    public String toString() {
        return "Guide[id=" + id + ", title=" + title + "]";
    }
}
