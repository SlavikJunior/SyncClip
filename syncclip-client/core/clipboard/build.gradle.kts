plugins {
    id("convention.kmp.library")
}

/**
 * Доступ к системному буферу обмена.
 *
 * Модуль инфраструктурный: контракт и правила (эхо, лимит размера, дедупликация)
 * живут в `commonMain`, платформенные реализации — в `androidMain`, `iosMain`,
 * `jvmMain`. Фичи видят только [io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway].
 */
