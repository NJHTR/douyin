-- Recommendation signals use the most recent progress update because
-- t_watch_history stores one upserted row per (user_id, video_id).
ALTER TABLE t_watch_history
    ADD INDEX idx_watch_history_user_finished_update
        (user_id, finished, update_time, id);
