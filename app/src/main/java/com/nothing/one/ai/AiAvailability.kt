package com.nothing.one.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the local engine every few seconds and exposes availability app-wide,
 * so every screen can show the same "engine on/off" truth without each one
 * pinging the socket. Polling starts on app start.
 */
@Singleton
class AiAvailability @Inject constructor(
    private val client: LocalAiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _checking = MutableStateFlow(true)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                _checking.value = true
                _available.value = try { client.isAvailable() } catch (_: Exception) { false }
                _checking.value = false
                delay(POLL_MS)
            }
        }
    }

    private companion object {
        const val POLL_MS = 6_000L
    }
}
