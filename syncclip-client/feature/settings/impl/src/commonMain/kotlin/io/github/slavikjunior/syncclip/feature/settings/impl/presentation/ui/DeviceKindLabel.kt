package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import org.jetbrains.compose.resources.StringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_icon_desktop
import syncclip.feature.settings.impl.generated.resources.settings_icon_laptop
import syncclip.feature.settings.impl.generated.resources.settings_icon_phone
import syncclip.feature.settings.impl.generated.resources.settings_icon_tablet
import syncclip.feature.settings.impl.generated.resources.settings_icon_tv
import syncclip.feature.settings.impl.generated.resources.settings_icon_watch

/**
 * Подпись иконки устройства для UI (INV-14).
 *
 * Имя enum-константы в интерфейс не попадает: оно зависит от кода и не
 * переводится. Маппер живёт в `presentation`, чтобы `domain` не знал о ресурсах.
 */
internal fun DeviceKind.labelRes(): StringResource = when (this) {
    DeviceKind.PHONE -> Res.string.settings_icon_phone
    DeviceKind.TABLET -> Res.string.settings_icon_tablet
    DeviceKind.LAPTOP -> Res.string.settings_icon_laptop
    DeviceKind.DESKTOP -> Res.string.settings_icon_desktop
    DeviceKind.WATCH -> Res.string.settings_icon_watch
    DeviceKind.TV -> Res.string.settings_icon_tv
}
