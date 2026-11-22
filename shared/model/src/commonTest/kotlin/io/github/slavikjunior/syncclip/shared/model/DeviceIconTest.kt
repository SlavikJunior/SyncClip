package io.github.slavikjunior.syncclip.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceIconTest {

    @Test
    fun `android tablet is classified as tablet`() {
        assertEquals(
            DeviceIcon.TABLET,
            DeviceIcon.resolve(DevicePlatform.ANDROID, isTablet = true, hasBattery = true),
        )
    }

    @Test
    fun `macbook is classified as laptop`() {
        assertEquals(
            DeviceIcon.LAPTOP,
            DeviceIcon.resolve(DevicePlatform.MACOS, isTablet = false, hasBattery = true),
        )
    }

    @Test
    fun `desktop without battery is classified as desktop`() {
        assertEquals(
            DeviceIcon.DESKTOP,
            DeviceIcon.resolve(DevicePlatform.LINUX, isTablet = false, hasBattery = false),
        )
    }
}
