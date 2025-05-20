package com.diyncrafts.web.app.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.diyncrafts.web.app.model.Video;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository("videoRepository")
public interface VideoRepository extends JpaRepository<Video, Long> {
    @Query("SELECT v FROM Video v JOIN v.category c WHERE c.name = :categoryName")
    List<Video> findByCategoryName(@Param("categoryName") String categoryName);

    @Query("SELECT v FROM Video v WHERE v.category.name IN :categoryNames")
    List<Video> findByCategoryNames(@Param("categoryNames") List<String> categoryNames);

    List<Video> findByDifficultyLevel(String difficultyLevel);

    @Modifying
    @Transactional
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + 1 WHERE v.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query("SELECT v FROM Video v WHERE v.user.id = :userId")
    List<Video> findVideosByUser(@Param("userId") UUID userId);

    @Query("SELECT v FROM Video v WHERE v.uploadDate >= :cutoff ORDER BY v.viewCount DESC")
    Page<Video> findTop5RecentByViewCount(@Param("cutoff") LocalDate cutoff, Pageable pageable);

    @Query("SELECT SUM(v.viewCount) FROM Video v WHERE v.category.id = :categoryId")
    Integer sumViewsByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT SUM(v.viewCount) FROM Video v WHERE v.category.id = :categoryId AND v.uploadDate BETWEEN :start AND :end")
    Integer sumViewsBetweenDates(
            @Param("categoryId") Long categoryId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}