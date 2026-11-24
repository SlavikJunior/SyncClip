package io.github.slavikjunior.syncclip.server.contracts

import io.github.slavikjunior.syncclip.server.contracts.error.PayloadTooLargeException
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.validation.IpAddresses
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Границы размеров из §12.3: 512 КиБ plaintext → 524 316 байт → 699 088 base64. */
class ValidatorsTest {

    private fun payloadOfSize(bytes: Int): String = Base64.getEncoder().encodeToString(ByteArray(bytes))

    @Test
    fun `payload at the maximum boundary is accepted`() {
        val decoded = Validators.encryptedPayload(payloadOfSize(Validators.MAX_CIPHERTEXT_BYTES.toInt()))
        assertEquals(524_316, decoded.size)
    }

    @Test
    fun `payload above the limit is rejected`() {
        assertFailsWith<PayloadTooLargeException> {
            Validators.encryptedPayload(payloadOfSize(Validators.MAX_CIPHERTEXT_BYTES.toInt() + 3))
        }
    }

    @Test
    fun `base64 length limit matches the design calculation`() {
        val maxBase64 = 4 * ((Validators.MAX_CIPHERTEXT_BYTES + 2) / 3)
        assertEquals(699_088L, maxBase64)
        assertEquals(Validators.MAX_PAYLOAD_BASE64_CHARS, maxBase64.toInt())
    }

    @Test
    fun `too small payload is rejected`() {
        assertFailsWith<PayloadTooLargeException> { Validators.encryptedPayload(payloadOfSize(27)) }
    }

    @Test
    fun `invalid base64 is a validation error`() {
        assertFailsWith<ValidationException> { Validators.encryptedPayload("not base64 !!!") }
    }

    @Test
    fun `hash must be 64 lowercase hex`() {
        val hash = "a".repeat(64)
        assertEquals(hash, Validators.payloadHash(hash))
        assertFailsWith<ValidationException> { Validators.payloadHash("5d41402abc4b2a76b9719d911017c592") }
        assertFailsWith<ValidationException> { Validators.payloadHash("A".repeat(64)) }
    }

    @Test
    fun `email is normalized and validated`() {
        assertEquals("user@example.com", Validators.email(" User@Example.COM "))
        assertFailsWith<ValidationException> { Validators.email("not-an-email") }
    }

    @Test
    fun `device name patches follow the contract regex`() {
        assertEquals("Work Laptop", Validators.patchDeviceName(" Work Laptop "))
        assertFailsWith<ValidationException> { Validators.patchDeviceName("macOS - 192.168.1.10") }
        assertFailsWith<ValidationException> { Validators.patchDeviceName("x".repeat(51)) }
    }

    @Test
    fun `targets are deduplicated and must not contain origin`() {
        val a = "550e8400-e29b-41d4-a716-446655440000"
        val b = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        assertEquals(listOf(a, b), Validators.targetDeviceIds(listOf(a, b, a), "other"))
        assertFailsWith<ValidationException> {
            Validators.targetDeviceIds(listOf(a), a)
        }
        assertFailsWith<ValidationException> {
            Validators.targetDeviceIds((1..11).map { "550e8400-e29b-41d4-a716-44665544000$it" }, "other")
        }
    }

    @Test
    fun `event timestamp rejects future and non positive values`() {
        val now = 1_700_000_000_000
        assertEquals(now, Validators.eventTimestamp(now, now))
        assertFailsWith<ValidationException> { Validators.eventTimestamp(0, now) }
        assertFailsWith<ValidationException> { Validators.eventTimestamp(now + 10 * 60 * 1000, now) }
    }

    @Test
    fun `canary size boundaries are enforced`() {
        val ok = Base64.getEncoder().encodeToString(ByteArray(80))
        assertEquals(ok, Validators.encryptedCanary(ok))
        assertFailsWith<ValidationException> { Validators.encryptedCanary("short") }
        assertFailsWith<ValidationException> { Validators.encryptedCanary("a".repeat(1025)) }
    }

    @Test
    fun `uuid accepts only canonical form`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(uuid, Validators.uuid(uuid.uppercase(), "deviceId"))
        assertFailsWith<ValidationException> { Validators.uuid("550e8400", "deviceId") }
    }

    @Test
    fun `password length window is enforced`() {
        assertTrue(Validators.password("12345678").isNotEmpty())
        assertFailsWith<ValidationException> { Validators.password("1234567") }
        assertFailsWith<ValidationException> { Validators.password("x".repeat(129)) }
    }

    @Test
    fun `ip parser rejects impossible literals and hostnames`() {
        // A8: regex принимал 999.999.999.999.
        assertNull(Validators.ipOrNull("999.999.999.999"))
        assertNull(Validators.ipOrNull("::::"))
        assertNull(Validators.ipOrNull("example.com"))
        assertNull(Validators.ipOrNull("localhost"))
        assertNull(Validators.ipOrNull(""))

        assertEquals("192.168.1.10", Validators.ipOrNull("192.168.1.10"))
        assertEquals("0:0:0:0:0:0:0:1", Validators.ipOrNull("::1"))
    }

    @Test
    fun `cidr parser handles ipv4 and ipv6`() {
        val v4 = IpAddresses.parseCidr("10.0.0.0/8")!!
        assertTrue(v4.contains(IpAddresses.parseLiteral("10.1.2.3")!!))
        assertFalse(v4.contains(IpAddresses.parseLiteral("11.1.2.3")!!))

        val single = IpAddresses.parseCidr("127.0.0.1")!!
        assertTrue(single.contains(IpAddresses.parseLiteral("127.0.0.1")!!))
        assertFalse(single.contains(IpAddresses.parseLiteral("127.0.0.2")!!))

        assertNotNull(IpAddresses.parseCidr("2001:db8::/32"))
        assertNull(IpAddresses.parseCidr("10.0.0.0/33"))
        assertNull(IpAddresses.parseCidr("not-a-cidr"))
    }
}
