package com.diyncrafts.web.app.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.web.app.model.Guide;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    List<Guide> findByVideoId(Long videoId, Pageable pageable);

    List<Guide> findByUserIdOrderByIdAsc(UUID userId);

    void deleteByVideoId(Long videoId);
}
