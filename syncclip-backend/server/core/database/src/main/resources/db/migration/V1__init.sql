-- ============================================================================
-- SyncClip — схема PostgreSQL 16 (канонический DDL)
-- ============================================================================
-- Статус: проект, согласован с docs/API_SPEC.yaml и docs/ARCHITECTURE.md §5.
-- Миграции: Flyway, файл соответствует V1__init.sql.
-- Приложение НЕ создаёт схему через Exposed SchemaUtils в production:
-- SchemaUtils допустим только в тестах.
--
-- Соглашения:
--   * идентификаторы генерирует приложение (UUID v7), БД не полагается на
--     gen_random_uuid(), чтобы порядок вставки был монотонным;
--   * время — TIMESTAMPTZ (архитектура писала TIMESTAMP; для сервера,
--     работающего в UTC и допускающего несколько инстансов, это безопаснее);
--   * hex-хэши хранятся в нижнем регистре и проверяются CHECK-ом;
--   * пользовательский контент шифруется на клиенте: сервер видит только
--     шифротекст (BYTEA) и метаданные маршрутизации.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. users — аккаунты
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash TEXT         NOT NULL,
    password_algo VARCHAR(32)  NOT NULL DEFAULT 'argon2id',
    plan          VARCHAR(16)  NOT NULL DEFAULT 'free',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_len_chk
        CHECK (char_length(email) BETWEEN 3 AND 255),
    -- Email нормализуется в нижний регистр приложением; CHECK страхует от
    -- появления двух «разных» аккаунтов на один ящик.
    CONSTRAINT users_email_lowercase_chk
        CHECK (email = lower(email)),
    CONSTRAINT users_password_hash_len_chk
        CHECK (char_length(password_hash) BETWEEN 20 AND 512),
    CONSTRAINT users_plan_chk
        CHECK (plan IN ('free', 'premium'))
);

CREATE UNIQUE INDEX users_email_lower_uk ON users (lower(email));

COMMENT ON TABLE  users IS 'Аккаунты SyncClip. Мастер-пароль и ключ E2EE здесь отсутствуют.';
COMMENT ON COLUMN users.password_hash IS 'Argon2id PHC-строка (m=65536,t=3,p=1) либо legacy BCrypt при password_algo=bcrypt.';
COMMENT ON COLUMN users.password_algo IS 'Алгоритм хеша пароля аккаунта: argon2id | bcrypt.';

-- ----------------------------------------------------------------------------
-- 2. devices — устройства аккаунта (включая гостевые)
-- ----------------------------------------------------------------------------
CREATE TABLE devices (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    os_type       VARCHAR(20)  NOT NULL,
    icon          VARCHAR(16)  NOT NULL,
    device_type   VARCHAR(16)  NOT NULL,
    token_version INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ,
    CONSTRAINT devices_name_len_chk
        CHECK (char_length(name) BETWEEN 1 AND 255),
    CONSTRAINT devices_os_type_chk
        CHECK (os_type IN ('Android', 'iOS', 'Windows', 'macOS', 'Linux')),
    CONSTRAINT devices_icon_chk
        CHECK (icon IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'WATCH', 'TV')),
    CONSTRAINT devices_type_chk
        CHECK (device_type IN ('permanent', 'guest')),
    -- Гость живёт до явного срока; постоянное устройство срока не имеет.
    CONSTRAINT devices_expiry_chk
        CHECK (
            (device_type = 'guest'    AND expires_at IS NOT NULL) OR
            (device_type = 'permanent' AND expires_at IS NULL)
        ),
    CONSTRAINT devices_token_version_chk
        CHECK (token_version >= 0),
    -- Составной ключ нужен для FK из таблиц, где user_id денормализован.
    CONSTRAINT devices_id_user_uk UNIQUE (id, user_id)
);

CREATE INDEX devices_user_created_idx ON devices (user_id, created_at);
CREATE INDEX devices_guest_expiry_idx ON devices (expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE  devices IS 'Устройства аккаунта. device_type=guest не учитывается в лимите 3 устройств.';
COMMENT ON COLUMN devices.name IS 'FR-6.1: PATCH допускает ^[A-Za-z0-9 -]{1,50}$; регистрация — до 255 (default "{osType} - {ip}").';
COMMENT ON COLUMN devices.token_version IS 'Инкремент инвалидирует все ранее выданные JWT устройства.';
COMMENT ON COLUMN devices.expires_at IS 'Для guest — момент, после которого сервер отвечает SESSION_REVOKED.';

-- ----------------------------------------------------------------------------
-- 3. encrypted_canary — контрольная строка для проверки мастер-пароля
-- ----------------------------------------------------------------------------
CREATE TABLE encrypted_canary (
    user_id         UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    encrypted_value TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- "syncclip-canary-" + UUID = 52 байта открытого текста;
    -- AES-256-GCM: IV(12) + ciphertext(52) + tag(16) = 80 байт -> base64 ~108.
    CONSTRAINT encrypted_canary_size_chk
        CHECK (octet_length(encrypted_value) BETWEEN 24 AND 1024)
);

COMMENT ON TABLE encrypted_canary IS 'Base64(AES-GCM(canary, key)). Сервер не знает key и мастер-пароль.';

-- ----------------------------------------------------------------------------
-- 4. offline_queue — транзитная очередь зашифрованных пакетов
-- ----------------------------------------------------------------------------
CREATE TABLE offline_queue (
    id                UUID        PRIMARY KEY,
    -- Монотонный порядок вставки: детерминированные "самый старый пакет"
    -- для вытеснения и стабильная сортировка pending без опоры на now().
    seq               BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id           UUID        NOT NULL,
    target_device_id  UUID        NOT NULL,
    event_id          UUID        NOT NULL,
    origin_device_id  UUID        NOT NULL,
    encrypted_payload BYTEA       NOT NULL,
    payload_hash      CHAR(64)    NOT NULL,
    client_timestamp  BIGINT      NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '48 hours'),

    -- Fan-out: одно событие адресуется нескольким устройствам, поэтому
    -- уникальность НЕ глобальная, а в рамках получателя.
    CONSTRAINT offline_queue_seq_uk UNIQUE (seq),
    CONSTRAINT offline_queue_target_event_uk UNIQUE (target_device_id, event_id),
    CONSTRAINT offline_queue_target_user_fk
        FOREIGN KEY (target_device_id, user_id)
        REFERENCES devices (id, user_id) ON DELETE CASCADE,
    CONSTRAINT offline_queue_hash_chk
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    -- 512 КиБ открытого текста + IV(12) + tag(16) = 524 316 байт.
    CONSTRAINT offline_queue_payload_size_chk
        CHECK (octet_length(encrypted_payload) BETWEEN 28 AND 524316),
    CONSTRAINT offline_queue_origin_chk
        CHECK (origin_device_id <> target_device_id),
    CONSTRAINT offline_queue_client_ts_chk
        CHECK (client_timestamp > 0),
    CONSTRAINT offline_queue_ttl_chk
        CHECK (expires_at > created_at)
);

-- Основной запрос: GET /sync/pending и доставка при подключении.
CREATE INDEX offline_queue_pending_idx
    ON offline_queue (target_device_id, seq);
CREATE INDEX offline_queue_expiry_idx
    ON offline_queue (expires_at);
CREATE INDEX offline_queue_origin_idx
    ON offline_queue (origin_device_id);

COMMENT ON TABLE offline_queue IS 'До 30 строк на target_device_id (лимит и вытеснение — в приложении под advisory lock).';
COMMENT ON COLUMN offline_queue.seq IS 'BIGINT IDENTITY: порядок вставки; вытеснение и пагинация идут по seq, а не по now().';
COMMENT ON COLUMN offline_queue.origin_device_id IS 'Без FK: удаление устройства-отправителя не должно стирать уже принятые пакеты.';
COMMENT ON COLUMN offline_queue.payload_hash IS 'SHA-256 hex в нижнем регистре; нужен клиенту для проверки целостности после расшифровки.';

-- ----------------------------------------------------------------------------
-- 5. fcm_tokens — registration tokens Firebase Cloud Messaging
-- ----------------------------------------------------------------------------
CREATE TABLE fcm_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    device_id  UUID        NOT NULL,
    token      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fcm_tokens_device_uk UNIQUE (device_id),
    CONSTRAINT fcm_tokens_token_uk  UNIQUE (token),
    CONSTRAINT fcm_tokens_device_user_fk
        FOREIGN KEY (device_id, user_id)
        REFERENCES devices (id, user_id) ON DELETE CASCADE
);

CREATE INDEX fcm_tokens_user_idx ON fcm_tokens (user_id);

COMMENT ON TABLE fcm_tokens IS 'Один актуальный токен на устройство; старый токен удаляется при ротации.';

-- ----------------------------------------------------------------------------
-- 6. auth_attempts — журнал попыток входа (FR-1.5, аудит)
-- ----------------------------------------------------------------------------
CREATE TABLE auth_attempts (
    id            UUID         PRIMARY KEY,
    user_id       UUID         REFERENCES users (id) ON DELETE CASCADE,
    device_id     UUID         REFERENCES devices (id) ON DELETE SET NULL,
    email_hash    CHAR(64),
    ip_address    INET,
    user_agent    VARCHAR(512),
    device_model  VARCHAR(255),
    kind          VARCHAR(32)  NOT NULL,
    success       BOOLEAN      NOT NULL,
    attempted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT auth_attempts_kind_chk
        CHECK (kind IN ('login', 'master_password', 'refresh_reuse', 'guest_exchange')),
    CONSTRAINT auth_attempts_subject_chk
        CHECK (user_id IS NOT NULL OR email_hash IS NOT NULL),
    CONSTRAINT auth_attempts_email_hash_chk
        CHECK (email_hash IS NULL OR email_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX auth_attempts_user_time_idx
    ON auth_attempts (user_id, kind, attempted_at DESC);
CREATE INDEX auth_attempts_device_time_idx
    ON auth_attempts (device_id, attempted_at DESC);
CREATE INDEX auth_attempts_email_time_idx
    ON auth_attempts (email_hash, attempted_at DESC);
CREATE INDEX auth_attempts_time_idx
    ON auth_attempts (attempted_at);

COMMENT ON TABLE auth_attempts IS 'IP/User-Agent — персональные данные; email хранится только как SHA-256. Retention 30 дней.';

-- ----------------------------------------------------------------------------
-- 7. master_password_locks — состояние блокировок FR-1.4
-- ----------------------------------------------------------------------------
-- failed_in_group 0..2; третья неудача запускает блокировку и сбрасывает
-- счётчик. lockout_count растёт монотонно, длительность блокировки
-- D(k) = 2 ч, если k % 3 == 2, иначе 5 мин (5 м, 5 м, 2 ч, 5 м, ...).
CREATE TABLE master_password_locks (
    user_id         UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    failed_in_group SMALLINT    NOT NULL DEFAULT 0,
    lockout_count   INTEGER     NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT mpl_group_chk CHECK (failed_in_group BETWEEN 0 AND 2),
    CONSTRAINT mpl_count_chk CHECK (lockout_count >= 0),
    CONSTRAINT mpl_state_chk
        CHECK (
            (locked_until IS NULL) OR
            (locked_until IS NOT NULL AND failed_in_group = 0)
        )
);

COMMENT ON TABLE master_password_locks IS 'Серверная блокировка ввода мастер-пароля: 3/5м, 3/5м, 3/2ч, цикл.';

-- ----------------------------------------------------------------------------
-- 8. refresh_tokens — семейства refresh-токенов (ротация + reuse detection)
-- ----------------------------------------------------------------------------
-- Модель: id — PK строки (UUIDv7), family_id — цепочка ротаций,
-- parent_id — предыдущая строка цепочки, jti_hash — SHA-256(jti).
-- Сам JWT не хранится; jti случайный (UUIDv4), поэтому hash достаточен
-- для проверки и не позволяет воспроизвести токен из дампа БД.
CREATE TABLE refresh_tokens (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL,
    device_id     UUID        NOT NULL,
    family_id     UUID        NOT NULL,
    parent_id     UUID        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    jti_hash      CHAR(64)    NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(32),

    CONSTRAINT refresh_tokens_jti_hash_uk UNIQUE (jti_hash),
    CONSTRAINT refresh_tokens_device_user_fk
        FOREIGN KEY (device_id, user_id)
        REFERENCES devices (id, user_id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_hash_chk
        CHECK (jti_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT refresh_tokens_expiry_chk
        CHECK (expires_at > issued_at),
    CONSTRAINT refresh_tokens_revoke_chk
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at),
    CONSTRAINT refresh_tokens_reason_chk
        CHECK (
            revoke_reason IS NULL OR
            revoke_reason IN ('rotated', 'logout', 'reuse', 'superseded', 'device_deleted')
        ),
    CONSTRAINT refresh_tokens_reason_state_chk
        CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX refresh_tokens_device_idx
    ON refresh_tokens (device_id, issued_at DESC);
CREATE INDEX refresh_tokens_family_idx
    ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx
    ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_active_idx
    ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_tokens IS 'Ротация: старый jti гасится (reason=rotated), новый наследует family_id; granular grace-окно 30 с.';
COMMENT ON COLUMN refresh_tokens.jti_hash IS 'SHA-256(jti) hex; lookup при /auth/refresh, plaintext jti в БД не хранится.';

-- ----------------------------------------------------------------------------
-- 9. guest_invites — одноразовые приглашения гостевого режима (FR-1.6)
-- ----------------------------------------------------------------------------
CREATE TABLE guest_invites (
    id                   UUID        PRIMARY KEY,
    user_id              UUID        NOT NULL,
    created_by_device_id UUID        NOT NULL,
    token_hash           CHAR(64)    NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL,
    used_at              TIMESTAMPTZ,
    used_by_device_id    UUID        REFERENCES devices (id) ON DELETE SET NULL,
    revoked_at           TIMESTAMPTZ,

    CONSTRAINT guest_invites_hash_uk UNIQUE (token_hash),
    CONSTRAINT guest_invites_creator_fk
        FOREIGN KEY (created_by_device_id, user_id)
        REFERENCES devices (id, user_id) ON DELETE CASCADE,
    CONSTRAINT guest_invites_hash_chk
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT guest_invites_ttl_chk
        CHECK (expires_at > created_at),
    CONSTRAINT guest_invites_used_chk
        CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT guest_invites_revoked_chk
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX guest_invites_pending_idx
    ON guest_invites (expires_at) WHERE used_at IS NULL AND revoked_at IS NULL;

COMMENT ON TABLE guest_invites IS 'Приглашение живёт 10 минут, гасится обменом на гостевой access-token, отзывается создателем.';

-- ----------------------------------------------------------------------------
-- 10. updated_at: единый триггер, чтобы значение не зависело от кода записи
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION syncclip_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION syncclip_set_updated_at();

CREATE TRIGGER encrypted_canary_set_updated_at
    BEFORE UPDATE ON encrypted_canary
    FOR EACH ROW EXECUTE FUNCTION syncclip_set_updated_at();

CREATE TRIGGER fcm_tokens_set_updated_at
    BEFORE UPDATE ON fcm_tokens
    FOR EACH ROW EXECUTE FUNCTION syncclip_set_updated_at();

CREATE TRIGGER master_password_locks_set_updated_at
    BEFORE UPDATE ON master_password_locks
    FOR EACH ROW EXECUTE FUNCTION syncclip_set_updated_at();

COMMIT;
