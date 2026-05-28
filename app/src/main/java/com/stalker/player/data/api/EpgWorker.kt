package com.stalker.player.data.api

import com.stalker.player.data.model.Channel
import com.stalker.player.data.model.EpgItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class EpgWorker(
    private val fetcher: suspend (channelId: String, size: Int) -> List<EpgItem>,
    private val cacheTtlMs: Long = 180_000L,
    private val debounceMs: Long = 150L
) {
    private data class Request(
        val key: String,
        val channelId: String,
        val size: Int,
        val onReady: (List<EpgItem>) -> Unit
    )

    private data class CacheEntry(
        val tsMs: Long,
        val items: List<EpgItem>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = CoroutineChannel<Request>(capacity = CoroutineChannel.UNLIMITED)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val lastRequestedAt = ConcurrentHashMap<String, Long>()

    init {
        scope.launch {
            for (request in queue) {
                val items = try {
                    fetcher(request.channelId, request.size)
                } catch (_: Exception) {
                    emptyList()
                }
                cache[request.key] = CacheEntry(System.currentTimeMillis(), items)
                request.onReady(items.take(request.size))
            }
        }
    }

    fun request(channel: Channel, size: Int = 3, onReady: (List<EpgItem>) -> Unit) {
        val key = channel.id.ifBlank { channel.name }
        val channelId = chooseChannelId(channel) ?: return
        val now = System.currentTimeMillis()
        val last = lastRequestedAt[key] ?: 0L
        if ((now - last) < debounceMs) return
        lastRequestedAt[key] = now

        val cached = cache[key]
        if (cached != null && (now - cached.tsMs) <= cacheTtlMs && cached.items.size >= size) {
            onReady(cached.items.take(size))
            return
        }

        queue.trySend(Request(key = key, channelId = channelId, size = size, onReady = onReady))
    }

    fun cancelPending() {
        while (true) {
            val result = queue.tryReceive()
            if (result.isFailure) break
        }
    }

    fun clear() {
        cancelPending()
        cache.clear()
        lastRequestedAt.clear()
    }

    fun stop() {
        clear()
        scope.cancel()
    }

    private fun chooseChannelId(channel: Channel): String? {
        val candidates = listOf(channel.id, channel.cmd)
        for (candidate in candidates) {
            val value = candidate.trim()
            if (value.isBlank()) continue
            val digits = value.filter { it.isDigit() }
            return digits.ifBlank { value }
        }
        return null
    }
}
