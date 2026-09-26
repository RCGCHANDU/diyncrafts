package com.diyncrafts.web.app.repository.jpa;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.diyncrafts.web.app.model.VideoDailyViews;

public interface VideoDailyViewsRepository extends JpaRepository<VideoDailyViews, VideoDailyViews.Key> {

    /**
     * Atomically adds one view for the given day (safe under concurrent requests).
     */
    @Modifying
    @Query(value = "INSERT INTO video_daily_views (video_id, view_date, views) VALUES (:videoId, :day, 1) "
            + "ON DUPLICATE KEY UPDATE views = views + 1", nativeQuery = true)
    void incrementViews(@Param("videoId") Long videoId, @Param("day") LocalDate day);

    /**
     * Views between {@code from} and {@code to} (both inclusive) of all videos in a category.
     */
    @Query("SELECT COALESCE(SUM(d.views), 0) FROM VideoDailyViews d, Video v "
            + "WHERE v.id = d.id.videoId AND v.category.id = :categoryId "
            + "AND d.id.viewDate BETWEEN :from AND :to")
    long sumViewsForCategory(@Param("categoryId") Long categoryId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
