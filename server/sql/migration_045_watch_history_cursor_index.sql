-- Keyset pagination for user watch history (update_time DESC, id DESC).
ALTER TABLE t_watch_history
    ADD INDEX idx_watch_history_user_update_id (user_id, update_time, id);
