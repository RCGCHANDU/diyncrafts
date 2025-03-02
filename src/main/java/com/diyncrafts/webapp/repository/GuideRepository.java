package com.diyncrafts.webapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.diyncrafts.webapp.model.Guide;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    @Query("SELECT g FROM Guide g WHERE g.videoId = :videoId ORDER BY g.id ASC")
    List<Guide> findByVideoId(@Param("videoId") String videoId, int offset, int limit);
}