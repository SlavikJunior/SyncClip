# Kache semantics

This file is the precise behaviour of the pipeline. Read it when a test fails, when a
screen shows the wrong origin, or when you are deciding between strategies. It is taken
from the implementation and its test matrix (JVM, Android host, iOS simulator).

## One read, step by step

`KmpCache.get(key, strategy, fetcher)` returns a cold `Flow`. Nothing runs until it is
collected.

1. The strategy decides whether the cache is consulted before or after the fetcher, and
   whether an expired record may be served.
2. A cache read goes L1 → L2. An L2 hit is deserialized and promoted into L1 with the
   **remaining** TTL, so promotion never extends a record's life. In a `ChainKmpCache`,
   the L2 hit reports `DISK` and the next read reports `MEMORY`.
3. A fetcher success is written through the tiers and emitted with `CacheOrigin.NETWORK`.
4. The flow completes after at most two emissions.

`CacheFirst` and `NetworkFirst` emit exactly one result. `CacheAndNetwork` and
`StaleWhileRevalidate` emit one or two. There is deliberately no `Loading` result:
`isLoading` belongs to `KacheStateHolder`, which knows when a read starts and ends.

## Strategy matrix

### `CacheFirst`

| Situation | Emission(s) |
|---|---|
| Fresh record in L1/L2 | `Success(value, MEMORY / DISK)`; fetcher is not called |
| Expired record, no fetcher | `Error(CacheMissException("No fetcher provided and cache miss"))` |
| Expired record, fetcher succeeds | `Success(value, NETWORK)`; value is written back |
| Expired record, fetcher throws | `Error(NetworkException(cause))` |

An expired record is a miss, and `get` evicts it. Use `StaleWhileRevalidate` when stale
data is useful.

### `NetworkFirst`

| Situation | Emission(s) |
|---|---|
| Fetch succeeds | `Success(value, NETWORK)`; value is written |
| Fetch fails, fresh record exists | `Success(value, MEMORY / DISK)` — the failure is **not** reported |
| Fetch fails, nothing cached | `Error(CacheMissException("Network fetch failed and no cached data available"))` |
| No fetcher, nothing cached | same `CacheMissException` |

The failed fetch is hidden on purpose: for the caller, "network down but data available"
is a success. Do not expect `NetworkException` from this strategy when a cache exists.

### `CacheAndNetwork`

| Situation | Emission(s) |
|---|---|
| Fresh record exists | `Success(value, MEMORY / DISK)` first |
| Fetch succeeds | then `Success(value, NETWORK)` |
| Fetch fails, cached value was emitted | nothing more; **no error** |
| Fetch fails, nothing cached | `Error(NetworkException(cause))` |
| No fetcher, nothing cached | `Error(CacheMissException("No fetcher provided and cache miss"))` |

### `StaleWhileRevalidate`

| Situation | Emission(s) |
|---|---|
| Record exists (fresh or expired) | `Success(value, MEMORY / DISK / MEMORY_STALE / DISK_STALE)` |
| Fetch succeeds | then `Success(value, NETWORK)`; the record is replaced |
| Fetch fails, cached value was emitted | nothing more; the stale record stays in place |
| Fetch fails, nothing cached | `Error(NetworkException(cause))` |

`*_STALE` origins appear only here. The stale record is kept while the fetch runs, so a
failed refresh does not destroy the fallback.

## TTL and expiry

- A record expires when `now - createdAt > ttl`. Exactly at `createdAt + ttl` it is still
  fresh; one millisecond later it is not.
- `ttl == null` on a record means it never expires. `put(key, value, ttl = null)` does
  **not** mean that: it means "use this cache's `defaultTtl`". If the cache's
  `defaultTtl` is null, the entry never expires. If it is set, `put` cannot create a
  non-expiring entry.
- `L1MemoryCache.get` drops an expired entry and returns null. `getStale` returns it
  without dropping it, and is what `StaleWhileRevalidate` uses.
- `L2KmpCache`/`ChainKmpCache` reads may see an expired record; the strategy decides.
- Expiry is judged against the cache's `TimeSource`. A `ChainKmpCache` uses L1's clock for
  both tiers, so pass the `TimeSource` to the `L1MemoryCache` you give the chain; the
  chain itself has no clock parameter.
- `L1MemoryCache.size()` counts expired-but-not-yet-reaped entries.

## Capacity, eviction and reaping

`maxSize` and `autoReapEvery` are `null` (off) by default; `evictionStrategy` defaults to
`LRU` but has no effect until `maxSize` is set.

| Parameter | Default | Semantics |
|---|---|---|
| `maxSize` | `null` (unbounded) | maximum number of L2 **records** (not bytes); `<= 0` also means unbounded |
| `evictionStrategy` | `LRU` | which record is dropped first: `LRU`, `FIFO`, `MRU`, `LIFO` |
| `autoReapEvery` | `null` | how often expired records are deleted; the first sweep waits one full interval |
| `touchGranularity` | 1 minute | how stale an access timestamp may get before a read refreshes it; `ZERO` = every read |

- After each `put`, the cache asks the engine for its `size()` and, if over the limit,
  removes candidates until it is back under. The key just written is never evicted.
- Expired records are always removed before anything the strategy ranks: they cannot be
  served by a read, so dropping them costs nothing.
- If `size()` returns `-1`, or `evictionCandidates` returns an empty list, the cache stays
  over the limit silently. `evictionCandidates` is the member custom backends forget.
- Access tracking is enabled by `maxSize`, not by a separate switch. Without a limit, no
  read ever touches `lastAccessedAt`, so a cache that was never bounded performs no extra
  writes.
- `touchGranularity` bounds access writes to at most one per key per window. A smaller
  window is more accurate and more expensive; `Duration.ZERO` writes on every read.
- `autoReapEvery` failures are swallowed and retried next interval; the loop is not killed
  by one bad sweep. `stopAutoReap()` cancels it and leaves the cache usable.
- `ChainKmpCache.autoReapEvery` sweeps L1 as well as L2.
- `KacheLogger.onEvict` fires only for capacity evictions, never for a reaped record.

## Errors

| Failure | Channel |
|---|---|
| Fetcher throws | `CacheResult.Error(NetworkException(cause))` (or a cache fallback, see strategies) |
| No fetcher and no data | `CacheResult.Error(CacheMissException(message))` |
| `StorageEngine` read fails | **thrown** `DiskReadException` from the flow |
| `StorageEngine` write fails | **thrown** `DiskWriteException` from `put` / the flow |
| Serializer fails on **write** | **thrown** `SerializationException` |
| Serializer fails on **read** — `L2KmpCache` | record is dropped, read reports a **miss** |
| Serializer fails on **read** — `ChainKmpCache` | **thrown** `SerializationException` (the chain does not catch it) |
| Backend throws something unmapped | `StorageTier` wraps it as `DiskReadException`/`DiskWriteException`; `ChainTier` lets it through unchanged |
| Coroutine cancellation | rethrown `CancellationException`; never wrapped |

`KacheException.UnknownKacheException` is part of the public hierarchy but nothing in the
library throws it today. Do not write `catch (e: UnknownKacheException)` expecting to see
backend failures; catch the specific types or `KacheException`.

Consequences:

- `CacheResult.Error.cachedData` is always null from the library's own pipeline. A
  fallback value arrives as a `Success` from `NetworkFirst`/`StaleWhileRevalidate`; the
  field exists for callers that construct their own error results.
- `KacheLogger.onError` is called only when a `CacheResult.Error` is produced. A thrown
  disk failure is not reported there.
- `KacheStateHolder` has no `catch`: a thrown storage/serialization failure leaves the
  launched coroutine, so an Android `viewModelScope` will crash unless the scope has a
  `CoroutineExceptionHandler` or the read is wrapped. Fetcher failures are safe — the
  pipeline turns them into `CacheResult.Error`.
- `chain.clear()` clears L1 first, then L2. A failure in L2 still propagates after L1 is
  already empty.

Exact messages:

- `"No fetcher provided and cache miss"` — `CacheFirst` (and `CacheAndNetwork` /
  `StaleWhileRevalidate` with no cached value and no fetcher).
- `"Network fetch failed and no cached data available"` — `NetworkFirst` after a failed
  fetch with nothing cached.

## `KacheStateHolder` transitions

| Event | `CachedState` after |
|---|---|
| construction | `data = null`, `origin = null`, `isLoading = false`, `error = null` |
| `load` for a **different** key | `CachedState(isLoading = true)` — the old value is dropped |
| `load` for the **same** key | previous value kept, `isLoading = true`, `error = null` |
| `Success` | `data` and `origin` set, `error = null`; `isLoading` unchanged |
| `Error` | `error` set; `data = cachedData ?: previous.data` |
| flow completes | `isLoading = false` |
| `retry()` before any `load` | no-op |
| `clearCache()` | cache emptied and state reset to `CachedState()` |
| `invalidate(key)` | cache entry dropped; state untouched |

A new `load` cancels the previous job. A generation counter prevents the superseded read's
`finally` block from clearing the loading flag of the read that replaced it.

## Retries

- `RetryPolicy.None` is the default: the fetcher runs exactly once.
- `RetryPolicy.Exponential(maxAttempts = n)` performs **n retries after the initial
  attempt** — `totalAttempts == n + 1`. `RetryPolicy.fixed(attempts = n, delay)` has the
  same meaning.
- `initialDelay` must be positive; `maxDelay >= initialDelay`; `multiplier >= 1`;
  `jitterRatio` in `0..1`. Invalid arguments throw `IllegalArgumentException` at
  construction.
- Jitter scales a delay into `[1 - jitterRatio, 1]` of the computed backoff.
- Retries apply only to the fetcher. Storage failures are not retried.
- A `retry` event is reported with the 1-based number of the attempt that failed and the
  delay before the next one.

## `KacheLogger` events

| Event | When |
|---|---|
| `onHit(key, origin)` | a tier answered; `origin` may be stale |
| `onMiss(key, strategy)` | nothing usable for the key |
| `onFetch(key, attempt)` | immediately before each fetcher invocation; `attempt` is 1-based |
| `onRetry(key, failedAttempt, delay)` | a retry was scheduled |
| `onError(key, error)` | a `CacheResult.Error` was produced |
| `onEvict(key)` | a record was dropped to honour `maxSize` |

Keys arrive already converted through the cache's `keyToString`. Values are never passed.
Implementations run inline on the read path and must be thread-safe and cheap.

## Security-relevant behaviour

- Payloads are **not encrypted**; anything that can read the cache files can read the
  values. Do not cache secrets unless the serializer encrypts them.
- Keys are encoded into file names reversibly. Do not put secrets in keys.
- Records are not authenticated: an attacker who can write the cache directory can keep a
  modified value alive. The library treats the directory as untrusted. `L2KmpCache` turns
  an undecodable record into a miss; `ChainKmpCache` currently throws on it, so handle
  that exception at the collection boundary.
- A file over `maxRecordBytes` (16 MiB by default) is refused on write and never loaded on
  read.
- JSON parsing refuses documents nested deeper than 64 levels before parsing.
- No network, no logging, no telemetry of its own.
