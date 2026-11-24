-- ============================================================================
-- SyncClip — V4: deviceId уникален внутри аккаунта (SEC-09)
-- ============================================================================
-- Глобальный PK devices(id) позволял занять чужой deviceId (squatting).
-- Теперь PK — (user_id, id): один и тот же клиентский UUID может принадлежать
-- разным аккаунтам, а выборка устройства всегда идёт в паре с user_id.
-- ============================================================================

-- 1. Сначала убираем FK, которые ссылаются на devices(id) (id перестанет быть уникальным).
ALTER TABLE auth_attempts DROP CONSTRAINT auth_attempts_device_id_fkey;
ALTER TABLE guest_invites DROP CONSTRAINT guest_invites_used_by_device_id_fkey;
ALTER TABLE device_sync_state DROP CONSTRAINT device_sync_state_device_id_fkey;

-- 2. device_sync_state: watermark в разрезе пользователя.
ALTER TABLE device_sync_state ADD COLUMN user_id UUID;
UPDATE device_sync_state s
SET user_id = d.user_id
FROM devices d
WHERE d.id = s.device_id;
ALTER TABLE device_sync_state ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE device_sync_state DROP CONSTRAINT device_sync_state_pkey;
ALTER TABLE device_sync_state ADD CONSTRAINT device_sync_state_pkey PRIMARY KEY (user_id, device_id);
ALTER TABLE device_sync_state
    ADD CONSTRAINT device_sync_state_device_user_fk
    FOREIGN KEY (device_id, user_id) REFERENCES devices (id, user_id) ON DELETE CASCADE;

-- 3. audit-поля больше не могут ссылаться на devices(id): id не уникален глобально.
-- Журнал хранит исторический идентификатор без FK (как offline_queue.origin_device_id).
COMMENT ON COLUMN auth_attempts.device_id IS
    'Исторический deviceId без FK: devices.id уникален только в паре с user_id.';

-- 4. used_by_device_id — ссылка на гостя того же пользователя; композитный FK.
ALTER TABLE guest_invites
    ADD CONSTRAINT guest_invites_used_by_device_fk
    FOREIGN KEY (used_by_device_id, user_id) REFERENCES devices (id, user_id)
    ON DELETE SET NULL (used_by_device_id);

-- 5. Только теперь PK devices можно заменить на составной.
ALTER TABLE devices DROP CONSTRAINT devices_pkey;
ALTER TABLE devices ADD CONSTRAINT devices_pkey PRIMARY KEY (user_id, id);

CREATE INDEX device_sync_state_device_idx ON device_sync_state (device_id);
