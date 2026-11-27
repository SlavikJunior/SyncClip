package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.coroutines.flow.Flow

/** Текстовый канал до сервера. Транспорт не знает, Ktor это или фейк в тесте. */
internal interface SyncSocket {

    suspend fun send(text: String)

    /** Входящие кадры после рукопожатия (включая initial pending). */
    fun incoming(): Flow<String>

    suspend fun close()
}
