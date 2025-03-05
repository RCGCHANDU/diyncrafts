package com.diyncrafts.webapp.repository.jpa;

import com.diyncrafts.webapp.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("videoRepository")
public interface VideoRepository extends JpaRepository<Video, Long> {
    @Query("SELECT v FROM Video v JOIN v.category c WHERE c.name = :categoryName")
    List<Video> findByCategoryName(@Param("categoryName") String categoryName);
    List<Video> findByDifficultyLevel(String difficultyLevel);
}