-- ============================================================================
-- SyncClip — V2: индексы для запросов, помеченных аудитом как Seq Scan
-- ============================================================================
-- PERF-02: findActiveRefreshChild (parent_id + revoked_at IS NULL) и
--          deleteExpiredRefreshTokens (expires_at) не имели индексов.
-- PERF-03: поиск anchor'а по event_id использовал Seq Scan (unique-индекс
--          начинается с target_device_id).
-- OPS-08:  очистка guest_invites фильтрует по expires_at без индекса
--          (partial-index покрывает только pending-строки).
-- ============================================================================

CREATE INDEX refresh_tokens_parent_idx
    ON refresh_tokens (parent_id, issued_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX refresh_tokens_expiry_idx
    ON refresh_tokens (expires_at);

CREATE INDEX offline_queue_event_target_idx
    ON offline_queue (event_id, target_device_id);

CREATE INDEX guest_invites_expiry_idx
    ON guest_invites (expires_at);
