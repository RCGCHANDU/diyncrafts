package com.diyncrafts.web.app.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;


import jakarta.persistence.*;

@Getter
@Setter
@Entity
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    // Null until a thumbnail has been uploaded or generated.
    @Column(length = 1024)
    private String thumbnailUrl;

    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;

    @Column(name = "views", nullable = false)
    private Long viewCount;

    @Column
    private String difficultyLevel;
    
    // DASH manifest URL; null until transcoding has completed.
    @Column(length = 1024)
    private String videoUrl;

    @ElementCollection
    @Column(name = "materials")
    @CollectionTable(name = "video_materials", joinColumns = @JoinColumn(name = "video_id"))
    private List<String> materialsUsed;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = true)
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Override
    public String toString() {
        return "Video[id=" + id + ", title=" + title + "]";
    }
}
