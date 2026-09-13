-- P1-07b: keep film/TV watch-history filtering inside the database.
-- Safe to apply after migration_045_watch_history_cursor_index.sql.
ALTER TABLE t_video_content
    ADD INDEX idx_video_content_category_video (text_category, video_id);
