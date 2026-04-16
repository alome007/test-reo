package dev.uclip.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory pairing store. Persistence to disk lands with phase 10 hardening. */
class PairingRegistry {
    private val _pairings = MutableStateFlow<Map<String, Pairing>>(emptyMap())
    val pairings: StateFlow<Map<String, Pairing>> = _pairings.asStateFlow()

    fun add(pairing: Pairing) {
        _pairings.value = _pairings.value + (pairing.deviceId to pairing)
    }

    fun remove(deviceId: String) {
        _pairings.value = _pairings.value - deviceId
    }

    fun get(deviceId: String): Pairing? = _pairings.value[deviceId]
}
