package io.github.slavikjunior.syncclip.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Мульти-превью для всех целевых форм-факторов приложения.
 *
 * Набор соответствует скиллу `adaptive`: телефон, складной (разложенный),
 * планшет и десктоп. Один и тот же экран проверяется в COMPACT/MEDIUM/
 * EXPANDED-диапазонах ширины, поэтому регрессии адаптивных раскладок видны
 * в IDE без запуска приложения на устройствах.
 */
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class FormFactorPreviews
