# Custom `StorageEngine` and serializers

Write a backend only when neither `FileStorageEngine` nor `RoomStorageEngine` fits (an
existing database, an encrypted store, an in-memory test double). The interface is small,
but three of its members have defaults that silently disable a feature.

## The contract

```kotlin
import io.github.slavikjunior.kache.core.EvictionStrategy
import io.github.slavikjunior.kache.core.StorageEngine
import io.github.slavikjunior.kache.core.StorageRecord

interface StorageEngine {
    suspend fun get(key: String): StorageRecord<*>?
    suspend fun put(key: String, record: StorageRecord<*>)
    suspend fun remove(key: String): Boolean
    suspend fun clear()
    suspend fun size(): Long

    // Optional; the defaults are what make a backend look healthy while a feature is dead.
    suspend fun removeExpired(now: Long): Long = 0L
    suspend fun touch(key: String, accessedAt: Long): Boolean = false
    suspend fun evictionCandidates(strategy: EvictionStrategy, limit: Int, now: Long): List<String> = emptyList()
}
```

| Member | Must return | If you skip it |
|---|---|---|
| `get` | the record, expired ones included, or null when absent | — |
| `put` | nothing; replaces any existing record | — |
| `remove` | true if a record was removed | — |
| `clear` | nothing | — |
| `size` | record count, or `-1` when unknown | capacity checks cannot run |
| `removeExpired` | number removed | expired records are never reaped on a schedule |
| `touch` | true if the timestamp was persisted | `LRU`/`MRU` rank by write order |
| `evictionCandidates` | keys valid for `remove`, worst first, expired first | **`maxSize` does nothing** |

Rules:

- **Thread safety is yours.** Operations are `suspend` but may run concurrently. In
  `commonMain` the portable primitive is `kotlinx.coroutines.sync.Mutex`.
- **Translate failures.** `DiskReadException` for reads, `DiskWriteException` for writes.
  `KacheException` subclasses that already exist pass through unchanged.
- **`size()` is a count, never bytes.** Do not label it as a byte size in logs or UI.
- **`get` may return expired records.** The strategy decides whether to serve one; do not
  filter them out, or `StaleWhileRevalidate` loses its input.
- **Do not reorder `evictionCandidates`.** `EvictionStrategy.recordComparator()` encodes the
  contract: expired records first, then the strategy's ordering. Returning keys in map
  iteration order silently turns every strategy into "whatever the map says".
- **Access tracking is best-effort.** The maintenance layer swallows failures from
  `touch` on purpose; a read must not fail because a timestamp could not be written.
- **Payloads are untrusted.** A backend should not trust that `record.data` decodes; that
  is the serializer's job.

## Compilable skeleton

A map-backed engine that implements all eight members. Useful as a test double, and as the
shape of a real adapter.

```kotlin
import io.github.slavikjunior.kache.core.EvictionStrategy
import io.github.slavikjunior.kache.core.StorageEngine
import io.github.slavikjunior.kache.core.StorageRecord
import io.github.slavikjunior.kache.core.recordComparator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MapStorageEngine : StorageEngine {

    private val records = mutableMapOf<String, StorageRecord<*>>()
    private val mutex = Mutex()

    override suspend fun get(key: String): StorageRecord<*>? = mutex.withLock { records[key] }

    override suspend fun put(key: String, record: StorageRecord<*>) {
        mutex.withLock { records[key] = record }
    }

    override suspend fun remove(key: String): Boolean = mutex.withLock {
        records.remove(key) != null
    }

    override suspend fun clear() {
        mutex.withLock { records.clear() }
    }

    override suspend fun size(): Long = mutex.withLock { records.size.toLong() }

    override suspend fun removeExpired(now: Long): Long = mutex.withLock {
        val expired = records.filterValues { it.isExpired(now) }.keys.toList()
        expired.forEach { records.remove(it) }
        expired.size.toLong()
    }

    override suspend fun touch(key: String, accessedAt: Long): Boolean = mutex.withLock {
        val existing = records[key] ?: return@withLock false
        records[key] = StorageRecord(
            value = existing.value,
            data = existing.data,
            createdAt = existing.createdAt,
            ttl = existing.ttl,
            lastAccessedAt = accessedAt,
        )
        true
    }

    override suspend fun evictionCandidates(
        strategy: EvictionStrategy,
        limit: Int,
        now: Long,
    ): List<String> = mutex.withLock {
        if (limit <= 0) return@withLock emptyList()

        val byStrategy = strategy.recordComparator()
        records.entries
            .sortedWith(
                // Expired records first: no read path can serve them, so they cost nothing
                // to drop. Then whatever the strategy ranks as the worst candidate.
                compareByDescending<Map.Entry<String, StorageRecord<*>>> { it.value.isExpired(now) }
                    .thenComparator { left, right -> byStrategy.compare(left.value, right.value) },
            )
            .take(limit)
            .map { it.key }
    }
}
```

Two details this skeleton demonstrates:

- The whole map is copied into a new `StorageRecord` on `touch`, because `StorageRecord`
  is immutable. A real backend issues an `UPDATE ... SET last_accessed_at = ?`.
- The cache asks `evictionCandidates` for one more candidate than the excess, so it can
  skip the record it has just written and still remove enough. Backends only have to
  honour `limit`.

## Serializer contract

```kotlin
import io.github.slavikjunior.kache.core.KacheException
import io.github.slavikjunior.kache.core.KacheSerializer

class EncryptingJsonSerializer<T>(
    private val delegate: KacheSerializer<T>,
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray,
) : KacheSerializer<T> {

    override fun serialize(value: T): ByteArray =
        try {
            encrypt(delegate.serialize(value))
        } catch (e: Exception) {
            throw KacheException.SerializationException(e)
        }

    override fun deserialize(bytes: ByteArray): T =
        try {
            delegate.deserialize(decrypt(bytes))
        } catch (e: Exception) {
            throw KacheException.SerializationException(e)
        }
}
```

- Serializers are **synchronous**. Do not block on IO inside one.
- Both methods must throw `KacheException.SerializationException` (or a subclass), not a
  raw exception: the L2 pipeline turns a failed decode into a miss, while `ChainKmpCache`
  propagates it, and a typed failure is what lets the caller tell a corrupt record from a
  bug.
- A secure serializer is the only way to encrypt values: the library does not encrypt
  anything. Keys still appear on disk, so they must not contain secrets.
- The shipped `KotlinxJsonSerializer` parses strictly and is the right choice unless you
  need a different format. For a list, wrap the element serializer:
  `KotlinxJsonSerializer(ListSerializer(Profile.serializer()))`.

## Testing a backend

Test the contract, not the wiring:

1. `put` then `get` returns an equal record, with `data` bytes intact.
2. `remove` returns true once and false afterwards.
3. `clear` empties everything.
4. `size` reflects the records actually stored.
5. `removeExpired(now)` removes exactly the expired records, including the
   `now == createdAt + ttl` boundary (still fresh at the boundary).
6. `touch` changes only `lastAccessedAt`.
7. `evictionCandidates` puts expired records first and applies `recordComparator()`.
8. Failures are translated into `KacheException`.
9. Concurrent writers on `Dispatchers.Default` do not lose records or corrupt payloads.
   The single-threaded `runTest` scheduler never interleaves coroutines, so a
   `Mutex` bug cannot show up there.

Run the same test class on every target the backend supports. A backend that passes on the
JVM can still fail on Kotlin/Native through a platform API that does not exist there.
