package com.diyncrafts.web.app.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.model.Video;

import java.util.List;
import java.util.UUID;

@Repository("videoRepository")
public interface VideoRepository extends JpaRepository<Video, Long> {
    @Query("SELECT v FROM Video v JOIN v.category c WHERE c.name = :categoryName")
    List<Video> findByCategoryName(@Param("categoryName") String categoryName);
    List<Video> findByDifficultyLevel(String difficultyLevel);

    @Modifying
    @Transactional
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + 1 WHERE v.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query("SELECT v FROM Video v WHERE v.user.id = :userId")
    List<Video> findVideosByUser(@Param("userId") UUID userId);
}