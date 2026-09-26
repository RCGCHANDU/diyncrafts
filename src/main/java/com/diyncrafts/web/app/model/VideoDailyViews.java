package com.diyncrafts.web.app.model;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Number of views a video received on one day; the basis for category growth statistics.
 * Rows are written with an atomic upsert (see {@code VideoDailyViewsRepository}).
 */
@Getter
@Setter
@Entity
@Table(name = "video_daily_views")
public class VideoDailyViews {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private long views;

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "video_id", nullable = false)
        private Long videoId;

        @Column(name = "view_date", nullable = false)
        private LocalDate viewDate;

        public Key(Long videoId, LocalDate viewDate) {
            this.videoId = videoId;
            this.viewDate = viewDate;
        }
    }
}
