package com.diyncrafts.web.app.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.diyncrafts.web.app.model.Guide;

@Repository
public interface GuideRepository extends JpaRepository<Guide, Long> {
    @Query("SELECT g FROM Guide g " +
           "WHERE g.video.id = :videoId " +
           "ORDER BY g.id ASC " +
           "OFFSET :offset ROWS " +
           "FETCH NEXT :limit ROWS ONLY")
    List<Guide> findByVideoId(
        @Param("videoId") Long videoId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    @Query("SELECT g FROM Guide g WHERE g.user.id = :userId")
    List<Guide> findGuidesByUser(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM guide ORDER BY id ASC LIMIT ?1 OFFSET ?2", nativeQuery = true)
    List<Guide> findAll(int limit, int offset); // Order: limit first, offset second
}