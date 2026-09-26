-- Per-day view counters so category growth compares views over time. Previously "growth" compared
-- lifetime views of videos uploaded in two date ranges. Views before this migration are not dated
-- and only count towards lifetime totals (video.views).

CREATE TABLE IF NOT EXISTS video_daily_views (
  video_id bigint NOT NULL,
  view_date date NOT NULL,
  views bigint NOT NULL,
  PRIMARY KEY (video_id, view_date),
  KEY idx_video_daily_views_date (view_date),
  CONSTRAINT fk_video_daily_views_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
