package io.github.slavikjunior.syncclip.core.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Мост `Task` Play services → корутина без лишней зависимости. */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val error = task.exception
        if (task.isSuccessful && error == null) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(error ?: IllegalStateException("Firebase task failed"))
        }
    }
}
