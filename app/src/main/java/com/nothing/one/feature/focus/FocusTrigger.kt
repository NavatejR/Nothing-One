package com.nothing.one.feature.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one-slot bridge between the assistant and the Focus tab. When a tool
 * asks for a focus session, the assistant pushes a request here; the Focus
 * ViewModel consumes it on start and clears the slot. No navigation
 * coupling: the request waits until the user actually opens Focus.
 */
@Singleton
class FocusTrigger @Inject constructor() {

    data class Request(val minutes: Int)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun request(minutes: Int) {
        _pending.value = Request(minutes.coerceIn(1, 180))
    }

    fun consume() {
        _pending.value = null
    }
}
