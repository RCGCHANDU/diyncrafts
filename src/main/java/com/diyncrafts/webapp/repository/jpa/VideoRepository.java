package com.diyncrafts.webapp.repository.jpa;

import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("videoRepository")
public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findByCategory(Category category);
    List<Video> findByDifficultyLevel(String difficultyLevel);
}