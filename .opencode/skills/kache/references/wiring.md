# Wiring Kache per platform

## Gradle

A Kotlin Multiplatform module takes `:cache-core` and, if it needs a durable tier,
`:cache-storage` in `commonMain`:

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") // for your own @Serializable models
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.slavikjunior.kache:cache-core:1.0.0")
            implementation("io.github.slavikjunior.kache:cache-storage:1.0.0")
        }
    }
}
```

A plain Android module does not need the KMP plugin on its side:

```kotlin
plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("io.github.slavikjunior.kache:cache-core:1.0.0")
    implementation("io.github.slavikjunior.kache:cache-android:1.0.0")
}
```

`kotlin("plugin.serialization")` must match the Kotlin version in use. `:cache-android`
already brings `:cache-storage` (which exposes `kotlinx-serialization-json` as `api`), so
the runtime needs no extra line; the compiler plugin is still required for the models you
annotate yourself.

### `iosX64`

`:cache-core` publishes `iosX64`; `:cache-storage` and `:cache-store-room` do not (Apple
dropped the target, and `androidx.sqlite` 2.7.1 has no variant). A project that declares
`iosX64` must keep both backends out of `commonMain`; `:cache-core` alone still compiles
there. Do not "fix" this by adding `iosX64` to the library — the dependency cannot be
resolved.

## Choosing an engine

| Platform | File engine | Room engine |
|---|---|---|
| Android | `createFromContext(context, cacheDirName)` | `createFromContext(context, databaseFileName)` |
| JVM | `FileStorageEngine(path)` | `createFromFile(path)` (parent directory must exist) |
| iOS | `createFromCachesDirectory(cacheDirName)` | `createFromCachesDirectory(databaseFileName)` |
| Common code | `FileStorageEngine(path)` | `RoomStorageEngineFactory.create(database)` |

The two `createFromContext` and the two `createFromCachesDirectory` functions live in
different packages and collide under wildcard imports. Import explicitly, or alias:

```kotlin
import io.github.slavikjunior.kache.storage.createFromCachesDirectory as fileCacheFromCaches
import io.github.slavikjunior.kache.store.room.createFromCachesDirectory as roomCacheFromCaches
```

## Android

### File-backed cache, shortest form

```kotlin
import android.content.Context
import io.github.slavikjunior.kache.android.l2Cache
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

@Serializable
data class Profile(val id: String, val name: String)

fun profileCache(context: Context) = l2Cache(
    context = context,
    serializer = KotlinxJsonSerializer(Profile.serializer()),
    defaultTtl = 12.hours,
    cacheDirName = "profiles",
    maxSize = 200,
)
```

The directory is `<app cacheDir>/profiles`, which Android excludes from Auto Backup and
may purge. The factory uses the application context internally.

### File engine plus the cache, when the engine must stay reachable

```kotlin
import io.github.slavikjunior.kache.android.l2Cache
import io.github.slavikjunior.kache.storage.createFromContext
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer

val engine = createFromContext(context, "profiles") // FileStorageEngine
val cache = l2Cache(
    storageEngine = engine,
    serializer = KotlinxJsonSerializer(Profile.serializer()),
)
// The engine stays reachable for size(), and cache.clear() empties it.
```

### Room-backed cache

```kotlin
import io.github.slavikjunior.kache.android.l2Cache
import io.github.slavikjunior.kache.store.room.createFromContext
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer

val engine = createFromContext(context, "profiles.db") // RoomStorageEngine
val cache = l2Cache(
    storageEngine = engine,
    serializer = KotlinxJsonSerializer(Profile.serializer()),
)
```

The Room factory stores the database in the app **cache** directory, applies the shipped
migrations, and moves a database written by an older Kache version out of the files
directory on first use. No Room Gradle plugin or KSP is needed to use the shipped
factories — `KacheDatabase` and its generated implementation are already in the artifact.

### Existing Room database

`RoomStorageEngineFactory.create(database)` wraps a database you configured yourself
(callbacks, journal mode, a different location). The database type is the library's
`KacheDatabase`; if you build it outside the factories you also choose the SQLite driver,
and you must add that driver dependency to your own module.

## JVM

```kotlin
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.storage.FileStorageEngine
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import java.io.File
import kotlin.time.Duration.Companion.hours

val engine = FileStorageEngine(File(cacheRoot, "profiles").absolutePath)

val cache = L2KmpCache(
    storageEngine = engine,
    valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
    defaultTtl = 1.hours,
    maxSize = 500,
)
```

Room on the JVM:

```kotlin
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.store.room.createFromFile
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer

val engine = createFromFile("/var/cache/myapp/kache.db") // parent must exist
val cache = L2KmpCache(
    storageEngine = engine,
    valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
)
```

## iOS

```kotlin
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import io.github.slavikjunior.kache.storage.createFromCachesDirectory

val engine = createFromCachesDirectory("profiles") // FileStorageEngine
val cache = L2KmpCache(
    storageEngine = engine,
    valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
    maxSize = 500,
)
```

Room on iOS:

```kotlin
import io.github.slavikjunior.kache.core.L2KmpCache
import io.github.slavikjunior.kache.storage.KotlinxJsonSerializer
import io.github.slavikjunior.kache.store.room.createFromCachesDirectory

val engine = createFromCachesDirectory("profiles.db") // RoomStorageEngine
val cache = L2KmpCache(
    storageEngine = engine,
    valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
    maxSize = 500,
)
```

Both factories use `NSCachesDirectory` with `create = true`; iOS may purge it, which is the
right lifetime for a cache. The file engine on iOS is implemented with kotlinx-io, the
JVM/Android one with `java.io`; the on-disk record format is shared.

## Where the cache lives

Create caches once per process, in a composition root, and hand them to repositories and
ViewModels. The sample app in the Kache repository is the reference implementation:

```kotlin
// commonMain: the shape both platforms share
class AppContainer(val profileCache: KmpCache<String, Profile>)

// androidMain
fun createAppContainer(context: Context): AppContainer = AppContainer(
    profileCache = l2Cache(context = context, serializer = KotlinxJsonSerializer(Profile.serializer())),
)

// iosMain
fun createAppContainer(): AppContainer = AppContainer(
    profileCache = L2KmpCache(
        storageEngine = createFromCachesDirectory("profiles.db"),
        valueSerializer = KotlinxJsonSerializer(Profile.serializer()),
    ),
)
```

One `maxSize` is enforced per engine. If several screens share one engine, one screen's
writes can evict another's records; give each cache its own directory/database when their
limits are independent.

## Android ViewModel and Compose, end to end

```kotlin
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.slavikjunior.kache.android.KacheableViewModel
import io.github.slavikjunior.kache.core.KmpCache
import io.github.slavikjunior.kache.core.KacheException

class ProfileViewModel(
    application: Application,
    cache: KmpCache<String, Profile>,
) : KacheableViewModel<String, Profile>(application, cache) {

    init {
        load(key = "profile:42", fetcher = { loadFreshProfile(it) })
    }

    fun refresh() = load(key = "profile:42", fetcher = { loadFreshProfile(it) })

    fun failureText(): String? = kacheState.value.error?.let { error ->
        when (error) {
            is KacheException.NetworkException -> "Network unavailable"
            is KacheException.CacheMissException -> "Nothing cached yet"
            else -> "Cache error"
        }
    }
}

@Composable
fun ProfileScreen(application: Application, container: AppContainer) {
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProfileViewModel(application, container.profileCache) }
        },
    )
    val state by viewModel.kacheState.collectAsStateWithLifecycle()

    // render state.data / state.origin / state.isLoading / state.error
}
```

`KacheableViewModel` requires an `Application`, so the default `ViewModelProvider` factory
cannot construct it; always supply a factory (or a DI graph) that also supplies the
application-scoped cache.

## Compose Multiplatform

Use `KacheStateHolder` directly; it has no Android types. Pass a scope from the screen's
state holder (`viewModelScope` in the JetBrains lifecycle `ViewModel`) and collect the
exposed `StateFlow` with `collectAsStateWithLifecycle` from the matching lifecycle
artifact.
