package com.diyncrafts.web.app.repository.jpa;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.diyncrafts.web.app.model.Video;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Query("SELECT v FROM Video v JOIN v.category c WHERE c.name = :categoryName ORDER BY v.id DESC")
    List<Video> findByCategoryName(@Param("categoryName") String categoryName);

    List<Video> findByDifficultyLevelOrderByIdDesc(String difficultyLevel);

    boolean existsByCategoryId(Long categoryId);

    /**
     * @return number of updated rows (0 when the video does not exist)
     */
    @Modifying
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + 1 WHERE v.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Query("SELECT v FROM Video v WHERE v.user.id = :userId ORDER BY v.id DESC")
    List<Video> findVideosByUser(@Param("userId") UUID userId);

    @Query("SELECT v FROM Video v WHERE v.uploadDate >= :cutoff ORDER BY v.viewCount DESC, v.id DESC")
    Page<Video> findTop5RecentByViewCount(@Param("cutoff") LocalDate cutoff, Pageable pageable);

    @Query("SELECT SUM(v.viewCount) FROM Video v WHERE v.category.id = :categoryId")
    Long sumViewsByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT SUM(v.viewCount) FROM Video v WHERE v.category.id = :categoryId AND v.uploadDate BETWEEN :start AND :end")
    Long sumViewsBetweenDates(@Param("categoryId") Long categoryId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
