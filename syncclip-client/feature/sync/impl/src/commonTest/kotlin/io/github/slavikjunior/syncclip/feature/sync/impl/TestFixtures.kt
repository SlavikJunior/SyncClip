package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardCapture
import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentity
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ProcessedEventRegistry
import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** Часы, которые двигает тест. */
internal class TestClock(private var currentMillis: Long = 1_000_000L) : EpochClock {

    override fun nowMillis(): Long = currentMillis

    fun advance(deltaMillis: Long) {
        currentMillis += deltaMillis
    }
}

/** Gateway без системного буфера: события публикует тест. */
internal class FakeClipboardGateway : ClipboardGateway {

    private val copies = MutableSharedFlow<ClipboardCapture>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    val inserted: MutableList<String> = mutableListOf()
    var current: String? = null
    private var lastCaptureMillis: Long = 0L

    override fun localCopies(): Flow<ClipboardCapture> = copies

    override suspend fun currentText(): String? = current

    override suspend fun insert(text: String) {
        inserted += text
        current = text
    }

    override fun lastLocalCaptureMillis(): Long = lastCaptureMillis

    suspend fun publish(capture: ClipboardCapture) {
        lastCaptureMillis = capture.capturedAtMillis
        copies.emit(capture)
    }
}

/** Настройки в памяти. */
internal class FakeClipboardSettingsStore(
    initial: ClipboardSettings = ClipboardSettings(),
) : ClipboardSettingsStore {

    private val values = MutableStateFlow(initial)

    /**
     * Имитация сбоя записи: переполненный диск, повреждённый файл DataStore.
     * Чтение при этом продолжает работать — так ведёт себя настоящий store.
     */
    var failWrites: Boolean = false

    private fun checkWrites() {
        if (failWrites) error("disk is full")
    }

    override val state: Flow<ClipboardSettings> = values

    override suspend fun current(): ClipboardSettings = values.value

    override suspend fun setOnline(isOnline: Boolean) {
        checkWrites()
        values.update { it.copy(isOnline = isOnline) }
    }

    override suspend fun setAutoInsert(autoInsert: Boolean) {
        checkWrites()
        values.update { it.copy(autoInsert = autoInsert) }
    }

    override suspend fun setMaxEntries(value: Int): Boolean {
        if (!io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules.isValidEntries(value)) {
            return false
        }
        checkWrites()
        values.update { it.copy(maxEntries = value) }
        return true
    }

    override suspend fun setTtlHours(value: Int): Boolean {
        if (!io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules.isValidTtlHours(value)) {
            return false
        }
        checkWrites()
        values.update { it.copy(ttlHours = value) }
        return true
    }

    override suspend fun setMaxBytes(value: Long): Boolean {
        if (!io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules.isValidBytes(value)) {
            return false
        }
        checkWrites()
        values.update { it.copy(maxBytes = value) }
        return true
    }

    override suspend fun setHistoryLimits(
        maxEntries: Int,
        ttlHours: Int,
        maxBytes: Long,
    ): Boolean {
        if (!io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules
                .validate(maxEntries, ttlHours, maxBytes)
        ) {
            return false
        }
        checkWrites()
        values.update {
            it.copy(maxEntries = maxEntries, ttlHours = ttlHours, maxBytes = maxBytes)
        }
        return true
    }

    fun update(settings: ClipboardSettings) {
        values.value = settings
    }
}

/** История в памяти: проверяет, что именно записал конвейер. */
internal class FakeHistoryWriter : ClipboardHistoryWriter {

    data class Saved(val text: String, val timestampMillis: Long, val sourceDeviceId: String?)

    val saved: MutableList<Saved> = mutableListOf()

    override suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?) {
        saved += Saved(text, timestampMillis, sourceDeviceId)
    }
}

/** Реестр дублей, управляемый тестом. */
internal class FakeProcessedEventRegistry : ProcessedEventRegistry {

    private val seen = mutableSetOf<String>()

    override suspend fun markProcessed(eventId: String): Boolean = seen.add(eventId)
}

/**
 * Room DAO в памяти.
 *
 * Повторяет контракт настоящего DAO, включая сортировку и фильтр закреплённых,
 * иначе тесты политики вытеснения проверяли бы сами себя.
 */
internal class FakeClipboardEventDao : ClipboardEventDao {

    val entities: MutableList<ClipboardEventEntity> = mutableListOf()

    override fun observePage(limit: Int, offset: Int): Flow<List<ClipboardEventEntity>> =
        flowOf(page("", limit, offset))

    override fun observeSearchPage(
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ClipboardEventEntity>> = flowOf(page(query, limit, offset))

    override suspend fun count(query: String): Int = filtered(query).size

    override suspend fun totalBytes(): Long = entities.sumOf {
        it.text.encodeToByteArray().size.toLong()
    }

    override suspend fun findById(id: String): ClipboardEventEntity? =
        entities.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ClipboardEventEntity) {
        entities.removeAll { it.id == entity.id }
        entities += entity
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        val index = entities.indexOfFirst { it.id == id }
        if (index >= 0) entities[index] = entities[index].copy(isPinned = pinned)
    }

    override suspend fun delete(id: String) {
        entities.removeAll { it.id == id }
    }

    override suspend fun oldestUnpinnedIds(limit: Int): List<String> = entities
        .filterNot { it.isPinned }
        .sortedBy { it.timestampMillis }
        .take(limit)
        .map { it.id }

    override suspend fun deleteByIds(ids: List<String>) {
        entities.removeAll { it.id in ids }
    }

    override suspend fun expiredUnpinnedIds(cutoffMillis: Long): List<String> = entities
        .filter { !it.isPinned && it.timestampMillis < cutoffMillis }
        .map { it.id }

    private fun page(query: String, limit: Int, offset: Int): List<ClipboardEventEntity> =
        filtered(query)
            .sortedByDescending { it.timestampMillis }
            .drop(offset)
            .take(limit)

    private fun filtered(query: String): List<ClipboardEventEntity> =
        if (query.isEmpty()) {
            entities.toList()
        } else {
            entities.filter { it.text.contains(query, ignoreCase = true) }
        }
}

/** Ключ E2EE, управляемый тестом. */
internal class FakeKeyProvider(var value: ByteArray? = null) : E2eeKeyProvider {

    override suspend fun key(): ByteArray? = value
}

/** Идентичность устройства без DataStore. */
internal class FakeDeviceIdentityStore(
    private val deviceId: String,
) : DeviceIdentityStore {

    override suspend fun identity(): DeviceIdentity = DeviceIdentity(
        deviceId = deviceId,
        deviceName = "Test device",
        osType = "Linux",
    )
}

/** Очередь Share Extension, управляемая тестом. */
internal class FakePendingShareSource(
    private var pending: List<String> = emptyList(),
    private var alreadySent: List<String> = emptyList(),
) : io.github.slavikjunior.syncclip.core.clipboard.PendingShareSource {

    override fun take(): List<String> {
        val result = pending
        pending = emptyList()
        return result
    }

    override fun takeAlreadySent(): List<String> {
        val result = alreadySent
        alreadySent = emptyList()
        return result
    }

    fun publish(texts: List<String>) {
        pending = texts
    }

    fun publishAlreadySent(texts: List<String>) {
        alreadySent = texts
    }
}

/** Исходящий поток, который наполняет тест. */
internal class FakePacketSource : ClipboardPacketSource, io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ServerPacketSource {

    private val packets = MutableSharedFlow<SyncPacket>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    override fun outgoingPackets(): Flow<SyncPacket> = packets

    override fun serverPackets(): Flow<SyncPacket> = packets

    suspend fun publish(packet: SyncPacket) {
        packets.emit(packet)
    }
}

/** Приёмник, который запоминает применённые пакеты. */
internal class FakeIncomingSink : ClipboardIncomingSink {

    val received: MutableList<IncomingClipboardPacket> = mutableListOf()

    override suspend fun receive(packet: IncomingClipboardPacket): IncomingClipboardAction {
        received += packet
        return IncomingClipboardAction.SAVE_AND_INSERT
    }
}

/** Обновление токена, управляемое тестом. */
internal class FakeSessionRefresher(
    var result: Boolean = true,
) : io.github.slavikjunior.syncclip.core.datastore.SessionRefresher {

    var calls: Int = 0

    override suspend fun ensureFresh(): Boolean {
        calls++
        return result
    }
}

/** LAN-сервер, который отдаёт обработчик тесту. */
internal class FakeLanServer : io.github.slavikjunior.syncclip.core.lan.LanServer {

    var handler: (suspend (io.github.slavikjunior.syncclip.core.lan.LanRawStream) -> Unit)? = null
    var port: Int = 41_000
    var started: Boolean = false

    /** Сколько первых `start` должны упасть (занят порт, нет сети). */
    var failStarts: Int = 0

    override suspend fun start(
        handle: suspend (io.github.slavikjunior.syncclip.core.lan.LanRawStream) -> Unit,
    ): Int {
        if (failStarts > 0) {
            failStarts--
            error("port is busy")
        }
        handler = handle
        started = true
        return port
    }

    override suspend fun stop() {
        started = false
        handler = null
    }
}

/** Дискавери с заданным списком пиров. */
internal class FakeLanDiscovery(
    initial: List<io.github.slavikjunior.syncclip.core.lan.LanPeer> = emptyList(),
) : io.github.slavikjunior.syncclip.core.lan.LanDiscovery {

    private val state = MutableStateFlow(initial)
    override val peers: kotlinx.coroutines.flow.StateFlow<List<io.github.slavikjunior.syncclip.core.lan.LanPeer>> =
        state
    var started: Boolean = false

    override suspend fun start() {
        started = true
    }

    override suspend fun stop() {
        started = false
    }

    fun publish(peers: List<io.github.slavikjunior.syncclip.core.lan.LanPeer>) {
        state.value = peers
    }
}

/** Диалер, который отдаёт заранее подготовленные потоки по порту. */
internal class FakeLanDialer(
    private val streams: Map<Int, BufferLanStream?>,
) : io.github.slavikjunior.syncclip.core.lan.LanDialer {

    val dialedPorts: MutableList<Int> = mutableListOf()

    override suspend fun dial(
        host: String,
        port: Int,
        timeoutMillis: Long,
    ): io.github.slavikjunior.syncclip.core.lan.LanRawStream? {
        dialedPorts += port
        return streams[port]
    }
}

/** Поток в памяти: входящие байты заранее, записанные — для проверки ACK. */
internal class BufferLanStream(
    private val input: ByteArray = ByteArray(0),
) : io.github.slavikjunior.syncclip.core.lan.LanRawStream {

    private var readOffset = 0
    private val written = mutableListOf<Byte>()
    private var writtenOffset = 0

    /** Последний запрошенный таймаут чтения: транспорт обязан защитить приём. */
    var lastReadTimeoutMillis: Long? = null

    override suspend fun readFully(size: Int): ByteArray? {
        if (readOffset + size > input.size) return null
        val result = input.copyOfRange(readOffset, readOffset + size)
        readOffset += size
        return result
    }

    override suspend fun write(bytes: ByteArray) {
        written.addAll(bytes.toList())
    }

    override suspend fun setReadTimeout(millis: Long) {
        lastReadTimeoutMillis = millis
    }

    override suspend fun close() = Unit

    fun nextWrittenFrame(): String? {
        if (writtenOffset + 4 > written.size) return null
        val length = ((written[writtenOffset].toInt() and 0xFF) shl 24) or
            ((written[writtenOffset + 1].toInt() and 0xFF) shl 16) or
            ((written[writtenOffset + 2].toInt() and 0xFF) shl 8) or
            (written[writtenOffset + 3].toInt() and 0xFF)
        if (writtenOffset + 4 + length > written.size) return null
        val bytes = ByteArray(length) { written[writtenOffset + 4 + it] }
        writtenOffset += 4 + length
        return bytes.decodeToString()
    }
}

/** Кодирует кадр так же, как сервер LAN: 4 байта длины + UTF-8. */
internal fun lanFrame(text: String): ByteArray {
    val payload = text.encodeToByteArray()
    return byteArrayOf(
        (payload.size ushr 24).toByte(),
        (payload.size ushr 16).toByte(),
        (payload.size ushr 8).toByte(),
        payload.size.toByte(),
    ) + payload
}

/** Сессия с зафиксированным userId. */
internal class FakeSignedInSession(val userId: String) : io.github.slavikjunior.syncclip.core.datastore.SessionStore {

    private val mutableState = MutableStateFlow<io.github.slavikjunior.syncclip.core.datastore.SessionState>(
        io.github.slavikjunior.syncclip.core.datastore.SessionState.SignedIn(
            io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot(
                userId = userId,
                accessToken = "access",
                refreshToken = "refresh",
            ),
        ),
    )

    override val state: kotlinx.coroutines.flow.StateFlow<io.github.slavikjunior.syncclip.core.datastore.SessionState> = mutableState

    override suspend fun refresh() = Unit

    override suspend fun save(session: io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot) = Unit

    override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit

    override suspend fun clear() = Unit

    override fun accessToken(): String? = "access"
}
