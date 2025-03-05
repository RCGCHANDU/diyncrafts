package com.diyncrafts.webapp.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.diyncrafts.webapp.model.Guide;

@Repository
public interface GuideRepository extends JpaRepository<Guide, Long> {
    @Query("SELECT g FROM Guide g WHERE g.videoId = :videoId ORDER BY g.id ASC")
    List<Guide> findByVideoId(@Param("videoId") String videoId, int offset, int limit);
}