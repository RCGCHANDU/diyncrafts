-- Record which video a transcoding task belongs to (ownership checks, idempotent processing).
-- Existing tasks keep NULL. Re-runnable.

SET @column_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'video_id');
SET @ddl := IF(@column_exists = 0, 'ALTER TABLE task ADD COLUMN video_id bigint NULL', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_video_id');
SET @ddl := IF(@index_exists = 0, 'CREATE INDEX idx_task_video_id ON task (video_id)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
