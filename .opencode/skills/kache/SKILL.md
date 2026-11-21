---
name: Kache
description: Use when adding, changing, or reviewing caching with the Kache Kotlin Multiplatform library (io.github.slavikjunior.kache) — choosing modules and tiers, wiring L1/L2 caches backed by files or Room, picking a CacheStrategy, handling CacheResult/CachedState/KacheException, or integrating Kache with ViewModels and Compose in Android/KMP projects. Triggers on "Kache", "cache-storage", "cache-store-room", "KacheStateHolder", "KacheableViewModel", "l2Cache", "CacheStrategy", "StaleWhileRevalidate", "TTL cache".
metadata:
  version: "1.0.0"
---

# Kache integration guide

Kache is a cache, not a data layer. The caller supplies the fetcher, the storage
location and the lifetime; Kache supplies strategy, TTL, two tiers, serialization,
eviction, retries, a state holder and observability hooks. Anything that looks like
networking, DI, encryption or automatic logging is **not** in this library.

Read this file before writing code. The mistakes that hurt are silent: a `maxSize`
that does nothing, a `null` TTL that means "use the default" rather than "forever",
a record that disappears after a model gains a field.

## 1. Modules

| Module | Targets | Adds | Take it when |
|---|---|---|---|
| `cache-core` | JVM, Android, `iosArm64`, `iosSimulatorArm64`, `iosX64` | `KmpCache`, `L1MemoryCache`, `L2KmpCache`, `ChainKmpCache`, strategies, `KacheStateHolder`, `StorageEngine` SPI | always |
| `cache-storage` | JVM, Android, `iosArm64`, `iosSimulatorArm64` | `FileStorageEngine`, `KotlinxJsonSerializer`, `StringSerializer` | durable per-key files |
| `cache-store-room` | JVM, Android, `iosArm64`, `iosSimulatorArm64` | `RoomStorageEngine` and factories | indexed/Room storage |
| `cache-android` | Android | `l2Cache(...)`, `KacheableViewModel`, `clearWhenScopeCancelled`, memory-pressure callbacks | Android app |

Coordinates (`1.0.0` is the current release; check Maven Central for a newer one):

```kotlin
dependencies {
    implementation("io.github.slavikjunior.kache:cache-core:1.0.0")
    implementation("io.github.slavikjunior.kache:cache-storage:1.0.0")   // optional
    implementation("io.github.slavikjunior.kache:cache-store-room:1.0.0") // optional
    implementation("io.github.slavikjunior.kache:cache-android:1.0.0")    // Android only
}
```

Consumer requirements: Kotlin **2.4.20+**, Gradle **9.x**, for Android AGP **9.x**,
`compileSdk 36+`, `minSdk 23`. `:cache-store-room` publishes **no `iosX64`** variant
(`androidx.sqlite` does not), so keep it out of `commonMain` if the project declares
`iosX64`. `:cache-android` brings `:cache-storage` and
`androidx.lifecycle:lifecycle-viewmodel` transitively.

## 2. Rules that prevent silent bugs

1. **Keys are identifiers, not data.** The file engine encodes the key into the file
   name, reversibly. Never put a token, e-mail or session id in a key. The default
   `keyToString` is `toString()`: always override it for data classes or any type whose
   `toString()` is not unique and stable.
2. **`put(key, value)` with no TTL means the cache's `defaultTtl`**, not "never
   expires". If `defaultTtl` is `null`, entries never expire. There is no way to write a
   non-expiring entry through `put` into a cache that has a default TTL.
3. **A cached type must be serializable and stable.** With `KotlinxJsonSerializer`,
   mark the type `@Serializable` and apply the `kotlin("plugin.serialization")` plugin.
   The serializer parses **strictly**: a persisted record that gains an unknown field, or
   loses a field the model declares without a default, fails to decode. `L2KmpCache`
   silently treats that as a miss and drops the record; `ChainKmpCache` throws (rule 9).
   Renaming a field is a cache format change: bump the key or clear the cache.
4. **`KotlinxJsonSerializer` takes a `KSerializer`.** For a list use
   `ListSerializer(Item.serializer())`; for a map use `MapSerializer(...)`. The
   no-argument constructor does not exist.
5. **`maxSize` is a record count, not bytes.** It is unbounded by default. Zero or a
   negative number also means unbounded. A custom `StorageEngine` that does not implement
   `evictionCandidates` makes `maxSize` silently do nothing.
6. **The cache must outlive the screen.** Construct it in a process/application-scoped
   container, not in a ViewModel or a composable. A cache owned by a screen cannot be
   shared with another screen and disappears when that screen is finished.
7. **Suspend does not mean off-main.** `FileStorageEngine` performs blocking
   `java.io`/kotlinx-io work on the collector's dispatcher. Reading or writing a
   file-backed cache from `Dispatchers.Main` blocks the frame. Room dispatches its own
   queries.
8. **Errors have two channels.** A failing *fetcher* becomes `CacheResult.Error`; a
   storage read/write failure or a serialization failure on **write** is **thrown** as
   `KacheException`. `KacheStateHolder` does not catch thrown failures. Handle both.
9. **A corrupt payload is a miss in `L2KmpCache` but is thrown by `ChainKmpCache`.** The
   L2-only pipeline drops an undecodable record and reports a miss; the two-tier chain
   lets the serializer exception escape. If you use a chain, handle that exception at the
   collection boundary, or use `L2KmpCache` when you need the library to drop corrupt
   records.
10. **`CacheResult.Error.cachedData` is always null from the pipeline.** Do not build a
    fallback on it. Fallbacks are produced by `NetworkFirst` and
    `StaleWhileRevalidate`, which emit a `Success` from cache instead of an error.
11. **One `KacheStateHolder` tracks one key.** A `load` for a different key drops the
    displayed value and cancels the previous read; a `load` for the same key keeps the
    value and clears the error. Use one holder per independently displayed value.
12. **Cache what the UI renders.** When a holder publishes the cached value, the stored
    type and the rendered type must be identical. Map DTO → domain inside the fetcher and
    cache domain models; caching DTOs forces a reverse mapper that a DTO cannot supply.
13. **`autoReapEvery` starts a coroutine that lives as long as the cache.** Only use it on
    an application-scoped cache, and call `stopAutoReap()` when the owner goes away while
    the cache stays usable.

## 3. Pick the entry point

| Need | Use |
|---|---|
| Fast memory-only cache, no disk | `L1MemoryCache` |
| Durable key/value, one file per key | `L2KmpCache` + `FileStorageEngine` |
| Durable storage with SQL/migrations | `L2KmpCache` + `RoomStorageEngine` |
| Memory in front of disk (usual choice) | `ChainKmpCache` |
| Android app, file cache, no custom wiring | `l2Cache(context, serializer)` from `:cache-android` |

`L1MemoryCache` is a low-level cache, not a `KmpCache`: it exposes `get(key)`, `getStale`,
`put`, `remove`, `clear`, `size` and `removeExpired`, but no strategy/fetcher flow, so
`KacheStateHolder` and `KacheableViewModel` cannot drive it. Its `maxSize` must be positive
(zero throws `IllegalArgumentException`). Use it directly for a screen-owned map, or put it
in front of a `StorageEngine` via `ChainKmpCache` when you want strategies.

The examples below use this model:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Profile(val id: String, val name: String)
```

Android shortcut, with all the defaults spelled out:

```kotlin
import android.content.Context
import io.github.slavikjunior.kache.android.l2Cache
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import kotlin.time.Duration.Companion.hours

fun profileCache(context: Context): L2KmpCache<String, Profile> = l2Cache(
    context = context,
    serializer = KotlinxJsonSerializer(Profile.serializer()),
    defaultTtl = 12.hours,   // the factory default is 1 hour
    cacheDirName = "profiles",
    maxSize = 200,           // null = unbounded; set it for anything long-lived
)
```

`l2Cache` returns `L2KmpCache<String, V>`: the key type is fixed to `String`. For typed
keys, construct the cache directly:

```kotlin
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.core.StorageEngine
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import kotlin.time.Duration.Companion.hours

data class ProfileKey(val userId: String)

fun typedCache(engine: StorageEngine): L2KmpCache<ProfileKey, Profile> = L2KmpCache(
    storageEngine = engine,
    valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
    keyToString = { "profile:${it.userId}" },
    defaultTtl = 1.hours,
)
```

KMP shared code, memory in front of a platform-chosen engine:

```kotlin
import io.github.slavikjunior.kache.core.ChainKmpCache
import io.github.slavikjunior.kache.core.L1MemoryCache
import io.github.slavikjunior.kache.core.StorageEngine
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun profileCache(engine: StorageEngine): ChainKmpCache<String, Profile> = ChainKmpCache(
    l1Cache = L1MemoryCache(maxSize = 100, defaultTtl = 5.minutes),
    l2Storage = engine,
    serializer = KotlinxJsonSerializer(Profile.serializer()),
    defaultTtl = 1.days,
    maxSize = 500,
)
```

Engine factories per platform — see `references/wiring.md`. The two
`createFromContext` functions (file and Room) and the two
`createFromCachesDirectory` functions collide under wildcard imports; import the one you
mean explicitly.

## 4. Read, write, invalidate

`KmpCache.get` returns a **cold `Flow<CacheResult<V>>`**, not a value:

```kotlin
import io.github.slavikjunior.kache.core.CacheResult
import io.github.slavikjunior.kache.core.CacheStrategy
import io.github.slavikjunior.kache.core.KmpCache
import kotlinx.coroutines.flow.last

suspend fun loadProfile(cache: KmpCache<String, Profile>, key: String): Profile? =
    when (val result = cache.get(key, CacheStrategy.CacheFirst) { loadFreshProfile(it) }.last()) {
        is CacheResult.Success -> result.data
        is CacheResult.Error -> null // result.error is typed; see section 5
    }
```

`loadFreshProfile` stands for your network call; the cache never performs IO beyond the
storage engine you gave it.

- `last()` = final outcome. `first()` = the cached value as soon as it is available, which
  matters for `CacheAndNetwork` and `StaleWhileRevalidate`.
- `CacheFirst` and `NetworkFirst` emit exactly one result, so `first()` and `last()` agree.
- `get` with no fetcher is a read-only cache: a miss is
  `CacheResult.Error(CacheMissException("No fetcher provided and cache miss"))`.

```kotlin
import io.github.slavikjunior.kache.core.KmpCache
import kotlin.time.Duration.Companion.minutes

suspend fun writeProfile(cache: KmpCache<String, Profile>, profile: Profile) {
    cache.put("profile:42", profile)                   // uses the cache's defaultTtl
    cache.put("profile:42", profile, ttl = 15.minutes) // explicit lifetime; replaces the entry
}

suspend fun dropProfile(cache: KmpCache<String, Profile>) {
    cache.invalidate("profile:42")                     // removes it from every tier
    cache.clear()                                      // empties the cache
}
```

For a `ChainKmpCache`, a write goes to both tiers and a read from L2 is promoted into L1
while keeping the original expiry. `invalidate` and `clear` clear both tiers.

## 5. Strategies and failures

| Strategy | Serves cached | Calls fetcher | Results emitted |
|---|---|---|---|
| `CacheFirst` | fresh only; expired is a miss | only on a miss | one |
| `NetworkFirst` | fresh only, as a fallback | yes | one |
| `CacheAndNetwork` | fresh only, then refresh | yes | one or two |
| `StaleWhileRevalidate` | **also expired**, then refresh | yes | one or two |

- A hit reports where it came from: `MEMORY`, `DISK`, `NETWORK`, or `*_STALE` for the
  expired value served by `StaleWhileRevalidate`.
- `NetworkFirst`, `CacheAndNetwork` and `StaleWhileRevalidate` **suppress** a fetch failure
  when a cached value was or can be emitted. The user sees data, not an error.
- `NetworkFirst` with an empty cache reports
  `CacheMissException("Network fetch failed and no cached data available")`, not
  `NetworkException` — the caller cannot act on the difference.
- `CacheFirst` with a failing fetcher reports `NetworkException`.
- Storage read/write failures and write-side serialization failures are **thrown**:
  `DiskReadException`, `DiskWriteException`, `SerializationException`. Coroutine
  cancellation is rethrown as `CancellationException`, never wrapped.
  (`UnknownKacheException` is declared for consumers but nothing in the library throws it.)
- A payload that fails to decode is dropped and reported as a **miss** by `L2KmpCache`,
  so a corrupt record cannot keep failing forever. `ChainKmpCache` does **not** do this:
  the serializer exception escapes from the flow. A chain therefore needs a `try/catch`
  around collection if the backend can hold records written against another schema.

Full emission-by-emission behaviour: `references/semantics.md`.

## 6. View layer

`KacheStateHolder` is the cross-platform half of the view layer. It owns `isLoading`,
cancels a superseded read and maps results into `CachedState`. It contains no Android
types, so it works in `commonMain`, on iOS and in Swift.

```kotlin
import io.github.slavikjunior.kache.core.CacheStrategy
import io.github.slavikjunior.kache.core.KacheStateHolder
import io.github.slavikjunior.kache.core.KmpCache
import kotlinx.coroutines.CoroutineScope

class ProfilePresenter(scope: CoroutineScope, private val cache: KmpCache<String, Profile>) {
    private val holder = KacheStateHolder(scope, cache)

    val state = holder.kacheState

    fun load() = holder.load(
        key = "profile:42",
        fetcher = { loadFreshProfile(it) },
        strategy = CacheStrategy.CacheFirst,
    )

    fun retry() = holder.retry()
    suspend fun invalidate() = holder.invalidate("profile:42")
    suspend fun clear() = holder.clearCache()
}
```

`CachedState` fields: `data`, `origin`, `isLoading`, `error`, plus `hasData`. `data` stays
populated while `isLoading` is true, so a refresh dims the screen instead of blanking it.
An error does not imply an empty screen: the previous value is kept as a fallback.

For a file-backed cache, put the reads on an IO dispatcher — `KacheStateHolder` launches
in the scope you give it:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

val holder = KacheStateHolder(viewModelScope + Dispatchers.IO, fileBackedCache)
```

On Android, `KacheableViewModel` is the same holder with `viewModelScope` supplied:

```kotlin
import android.app.Application
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.slavikjunior.kache.android.KacheableViewModel
import io.github.slavikjunior.kache.core.KmpCache

class ProfileViewModel(
    application: Application,
    cache: KmpCache<String, Profile>,
) : KacheableViewModel<String, Profile>(application, cache) {

    fun start() = load("profile:42", fetcher = { loadFreshProfile(it) })
}

fun profileViewModelFactory(
    application: Application,
    container: AppContainer,
) = viewModelFactory {
    initializer {
        ProfileViewModel(application, container.profileCache) // cache is app-scoped
    }
}
```

Compose renders `kacheState` directly:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.kacheState.collectAsStateWithLifecycle()

    when {
        state.data == null && state.isLoading -> CircularProgressIndicator()
        state.data == null && state.error != null -> Text("Failed to load")
        else -> Text(state.data?.name.orEmpty())
    }

    if (state.isLoading && state.data != null) Text("Refreshing…")
}
```

Do not create a cache inside a composable, a ViewModel's `init`, or any scope that dies
with the screen unless that is deliberate.

## 7. Android lifecycle and memory

```kotlin
import android.app.Application
import io.github.slavikjunior.kache.android.clearWhenScopeCancelled
import io.github.slavikjunior.kache.android.registerCacheMemoryPressureCallbacks
import io.github.slavikjunior.kache.android.unregisterCacheMemoryPressureCallbacks
import io.github.slavikjunior.kache.core.KmpCache
import kotlinx.coroutines.CoroutineScope

fun wireCacheLifecycle(
    application: Application,
    cache: KmpCache<String, Profile>,
    viewModelScope: CoroutineScope,
    appScope: CoroutineScope,
) {
    // Only for a cache that should die with the screen. Fires on completion or cancellation.
    cache.clearWhenScopeCancelled(viewModelScope)

    // Application-scoped registration; unregister when the caches go away.
    val callbacks = application.registerCacheMemoryPressureCallbacks(appScope, cache)
    application.unregisterCacheMemoryPressureCallbacks(callbacks)
}
```

Only `TRIM_MEMORY_COMPLETE` and `onLowMemory` clear data; intermediate trims deliberately
keep it. Registration retains the caches for the process lifetime until unregistered.

## 8. Custom `StorageEngine` (only when no shipped backend fits)

Five members are required: `get`, `put`, `remove`, `clear`, `size`. Three have defaults
that silently disable a feature:

| Member | Default | What breaks without it |
|---|---|---|
| `removeExpired(now)` | returns `0` | expired records are never deleted on a schedule; storage grows |
| `touch(key, accessedAt)` | returns `false` | `LRU`/`MRU` degrade to write order |
| `evictionCandidates(strategy, limit, now)` | empty list | **`maxSize` does nothing at all** |

Implementations must be thread-safe and translate low-level failures into
`KacheException`. `size()` is a record count and may return `-1` when unknown; it is not a
byte size. Use `EvictionStrategy.recordComparator()` for ranking. Skeleton and the exact
ordering contract: `references/backend.md`.

## 9. Tests

Inject `MutableTimeSource` instead of sleeping. `L1MemoryCache` and `L2KmpCache` accept a
`TimeSource`; `ChainKmpCache` has no clock parameter and uses L1's.

```kotlin
import io.github.slavikjunior.kache.core.L1MemoryCache
import io.github.slavikjunior.kache.core.MutableTimeSource
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

val clock = MutableTimeSource(initialTimeMillis = 0L)
val cache = L1MemoryCache<String, String>(
    maxSize = 10,
    defaultTtl = 100.milliseconds,
    timeSource = clock,
)
cache.put("k", "v")
clock.advance(100.milliseconds)
assertNotNull(cache.get("k"), "expiry is strictly after createdAt + ttl")
clock.advance(1.milliseconds)
assertNull(cache.get("k"))
```

For an L2 cache in tests, use a small `StorageEngine` over a map (the sample has one) or
a real `FileStorageEngine` in a temporary directory. The real `KotlinxJsonSerializer`
belongs in the test: it parses strictly, so a round-trip catches a missing `@Serializable`
or a dropped field. Assert on `CacheOrigin` and on the emissions, not only on the value.

## 10. Review checklist

- [ ] Keys are normalized, unique, stable and contain no secrets.
- [ ] `keyToString` is overridden for any non-string key type.
- [ ] Every type cached through `KotlinxJsonSerializer` is `@Serializable`; lists use
      `ListSerializer`; renamed or removed fields were treated as a cache format change.
- [ ] `maxSize` is set for any cache that outlives a screen, and the backend implements
      `evictionCandidates` (and `touch` for LRU/MRU).
- [ ] `autoReapEvery` is used only on a long-lived cache, with `stopAutoReap()` available.
- [ ] The cache lives in an application/process-scoped container, not in a ViewModel.
- [ ] File-backed reads/writes do not run on the main thread.
- [ ] Both error channels are handled: `CacheResult.Error` and thrown `KacheException`.
- [ ] No fallback logic depends on `CacheResult.Error.cachedData`.
- [ ] UI distinguishes cache from network via `origin` when that matters to the user.
- [ ] Memory-pressure callbacks are unregistered when the caches go away.
- [ ] A project with `iosX64` does not put `:cache-store-room` in `commonMain`.

## References

| File | Read it when |
|---|---|
| `references/wiring.md` | full per-platform wiring, factories, app-container and Compose examples |
| `references/semantics.md` | exact strategy emissions, TTL/eviction/reaping rules, error matrix, logger events |
| `references/backend.md` | implementing or reviewing a custom `StorageEngine` or serializer |
| `../../../README.md` (library repo) | API tour and per-recipe examples |
| `SECURITY.md` (library repo) | threat model and what the library does not protect |

If you are working **inside the Kache repository**, its build commands and target traps are
in `../../../AGENTS.md`; the two that matter most: the Android unit-test task is
`testAndroidHostTest` (not `testDebugUnitTest`, which silently runs nothing), and
`./gradlew check` does not verify every iOS target — compile them explicitly.

To use this skill in a consumer repository, copy this directory to
`<repo>/.opencode/skills/kache`, or link it into the global `~/.agents/skills/kache`;
OpenCode discovers both locations.
