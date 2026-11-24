-- ============================================================================
-- SyncClip — V5: Firebase Auth (SEC-10)
-- ============================================================================
-- Additive Firebase Auth: пользователь может быть создан по verified Firebase
-- ID token. Пароль не используется; в password_hash хранится marker
-- `firebase-auth:<uid>`, парольные маршруты в firebase-режиме отключены.
-- ============================================================================

ALTER TABLE users ADD COLUMN firebase_uid VARCHAR(128);

CREATE UNIQUE INDEX users_firebase_uid_uk
    ON users (firebase_uid)
    WHERE firebase_uid IS NOT NULL;

COMMENT ON COLUMN users.firebase_uid IS
    'Firebase Auth UID (sub); NULL для аккаунтов с собственным паролем.';
