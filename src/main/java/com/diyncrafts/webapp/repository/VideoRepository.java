package com.diyncrafts.webapp.repository;

import com.diyncrafts.webapp.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findByCategory(String category);
    List<Video> findByDifficultyLevel(String difficultyLevel);
}