package io.github.slavikjunior.syncclip.feature.settings.impl

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceNameError
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ValidateDeviceNameUseCaseTest {

    private val useCase = ValidateDeviceNameUseCase()

    @Test
    fun `valid name is accepted`() {
        assertEquals(Result.success("Work Laptop-1"), useCase("Work Laptop-1"))
    }

    @Test
    fun `empty name is rejected`() {
        assertTrue(useCase("").exceptionOrNull() is DeviceNameError.Empty)
    }

    @Test
    fun `too long name is rejected`() {
        assertTrue(useCase("a".repeat(51)).exceptionOrNull() is DeviceNameError.TooLong)
    }

    @Test
    fun `illegal characters are rejected`() {
        assertTrue(useCase("Work_Laptop").exceptionOrNull() is DeviceNameError.InvalidFormat)
    }
}
