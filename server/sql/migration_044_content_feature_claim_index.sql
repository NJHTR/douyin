-- P1-04: support bounded feature-worker recovery and lease expiry scans.
ALTER TABLE t_video_content
    ADD INDEX idx_extract_recovery (extract_status, update_time, video_id);
