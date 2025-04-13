package com.diyncrafts.web.app.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Entity
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @NotBlank(message = "Thumbnail URL is required")
    @Column(nullable = false)
    private String thumbnailUrl;

    @NotNull(message = "Upload date is required")
    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;

    @NotNull(message = "View count is required")
    @Column(name = "views", nullable = false)
    private Long viewCount;

    @Column
    private String difficultyLevel;
    
    @NotBlank(message = "Video URL is required")
    @Column(nullable = false)
    private String videoUrl;

    @ElementCollection
    @Column(name = "materials")
    @CollectionTable(name = "video_materials", joinColumns = @JoinColumn(name = "video_id"))
    private List<String> materialsUsed;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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