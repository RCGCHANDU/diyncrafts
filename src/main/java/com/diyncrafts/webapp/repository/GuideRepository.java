package com.diyncrafts.webapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.webapp.model.Guide;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    List<Guide> findByVideoId(String videoId);
}