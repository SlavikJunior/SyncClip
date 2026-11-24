-- ============================================================================
-- SyncClip — V3: watermark последнего ACK на устройстве (FUNC-05)
-- ============================================================================
-- Клиент после реконнекта передаёт lastEventId последнего полученного пакета.
-- Если пакет уже ACK-нут и удалён из очереди, без watermark сервер не может
-- отличить «своё удалённое» событие от чужого и отдаёт всю очередь (дубликаты).
-- ============================================================================

CREATE TABLE device_sync_state (
    device_id           UUID        PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    last_acked_event_id UUID,
    last_acked_seq      BIGINT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- event_id и seq обновляются только вместе.
    CONSTRAINT device_sync_state_ack_chk
        CHECK ((last_acked_event_id IS NULL) = (last_acked_seq IS NULL))
);

COMMENT ON TABLE device_sync_state IS
    'Watermark последнего ACK: позволяет отдать pending после уже удалённого lastEventId без повторной выдачи старых пакетов.';
