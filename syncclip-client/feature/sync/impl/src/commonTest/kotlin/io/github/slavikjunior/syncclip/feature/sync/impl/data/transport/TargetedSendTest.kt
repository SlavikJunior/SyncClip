package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.core.clipboard.TargetedSendResult
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeDeviceIdentityStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeKeyProvider
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.TestClock
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-016: адресная отправка уходит одним кадром с `targetDeviceIds`. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TargetedSendTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 7 } }
    private val outgoing = FakePacketSource()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()
    private val factory = FakeSocketFactory()
    private val transport = SyncTransport(
        socketFactory = factory,
        keyProvider = FakeKeyProvider(key),
        identity = FakeDeviceIdentityStore("device-a"),
        serverPackets = outgoing,
        incomingSink = FakeIncomingSink(),
        settings = settings,
        cipher = cipher,
        clock = clock,
    )

    @Test
    fun `targeted packet carries the device list only`() = runTest {
        startTransport()

        val result = transport.send("hello", listOf("phone-1", "tablet-2"))
        runCurrent()

        assertEquals(TargetedSendResult.Queued, result)
        val frame = factory.created.single().sent.single()
        val body = Json.parseToJsonElement(frame).jsonObject
        assertEquals(
            listOf("phone-1", "tablet-2"),
            body["targetDeviceIds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            "hello",
            cipher.decrypt(body["encryptedPayload"]!!.jsonPrimitive.content, key),
        )
        assertEquals(PayloadHash.of("hello"), body["payloadHash"]?.jsonPrimitive?.content)
    }

    @Test
    fun `offline transport refuses to promise delivery`() = runTest {
        startTransport()
        settings.setOnline(false)
        runCurrent()

        assertEquals(TargetedSendResult.Offline, transport.send("hello", listOf("phone-1")))
    }

    @Test
    fun `empty clipboard is rejected before encryption`() = runTest {
        startTransport()

        assertEquals(TargetedSendResult.Empty, transport.send("   ", listOf("phone-1")))
        assertTrue(factory.created.single().sent.isEmpty())
    }

    private fun TestScope.startTransport() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.run()
        }
        runCurrent()
    }
}
